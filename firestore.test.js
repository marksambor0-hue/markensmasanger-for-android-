const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validChannelPayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    name: "#general-exe-port",
    topic: "Main desktop .exe messenger port channel",
    category: "CHANNEL",
    linkedRepo: "JetBrains/kotlin",
    unreadCount: 0,
    isPinned: true,
    lastMessageText: "Desktop bridge initialized",
    lastSenderName: "System",
    createdAt: now,
    updatedAt: now,
  };
}

function validMessagePayload(uid, channelId) {
  const now = new Date();
  return {
    userId: uid,
    channelId: channelId,
    senderName: "Alice Dev",
    senderHandle: "@alice",
    text: "Ported packet from desktop .exe client!",
    messageType: "CODE_SNIPPET",
    codeLanguage: "kotlin",
    codeSnippet: "fun main() = println(\"GitPulse Android\")",
    commitSha: "a1b2c3d",
    isPinned: false,
    reactionsCount: 1,
    createdAt: now,
    updatedAt: now,
  };
}

function validBridgeProfilePayload(uid) {
  const now = new Date();
  return {
    userId: uid,
    displayName: "Alice Dev",
    statusText: "Porting .exe packets to Android",
    presenceState: "CODING",
    githubRepo: "JetBrains/kotlin",
    exeBridgeMode: "ACTIVE_SYNC",
    syncedPacketsCount: 42,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user: cannot read or write channels, messages, or profiles", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("channels").get());
  await assertFails(unauthDb.collection("messages").get());
  await assertFails(
    unauthDb.collection("channels").doc("ch_1").set(validChannelPayload(ALICE_UID))
  );
});

test("Authenticated user: can create and query own channel with matching userId filter", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("channels").doc("ch_alice").set(validChannelPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("channels").where("userId", "==", ALICE_UID).get()
  );
});

test("Authenticated user: cannot read or update another user's channel", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context
      .firestore()
      .collection("channels")
      .doc("ch_bob")
      .set(validChannelPayload(BOB_UID));
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(aliceDb.collection("channels").doc("ch_bob").get());
  await assertFails(
    aliceDb.collection("channels").doc("ch_bob").update({
      topic: "Hijacked topic",
      updatedAt: new Date(),
    })
  );
});

test("Shadow Update & Enum Poisoning: rejects unknown fields or invalid category", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb
      .collection("channels")
      .doc("ch_shadow")
      .set({
        ...validChannelPayload(ALICE_UID),
        isAdmin: true,
      })
  );
  await assertFails(
    aliceDb
      .collection("channels")
      .doc("ch_bad_enum")
      .set({
        ...validChannelPayload(ALICE_UID),
        category: "INVALID_CATEGORY",
      })
  );
});

test("Message relational consistency: requires existing parent channel owned by caller", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  // Fails when channel does not exist (orphaned message)
  await assertFails(
    aliceDb
      .collection("messages")
      .doc("msg_orphan")
      .set(validMessagePayload(ALICE_UID, "non_existent_ch"))
  );

  // Succeeds after parent channel is created by Alice
  await assertSucceeds(
    aliceDb.collection("channels").doc("ch_alice").set(validChannelPayload(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb
      .collection("messages")
      .doc("msg_valid")
      .set(validMessagePayload(ALICE_UID, "ch_alice"))
  );
});

test("BridgeProfile: owner can create and update own profile, cannot access another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb
      .collection("bridge_profiles")
      .doc(ALICE_UID)
      .set(validBridgeProfilePayload(ALICE_UID))
  );
  await assertFails(
    aliceDb
      .collection("bridge_profiles")
      .doc(BOB_UID)
      .set(validBridgeProfilePayload(BOB_UID))
  );
});
