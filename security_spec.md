# Security Specification — GitPulse Messenger

## 1. Data Invariants
1. **Multi-User Ownership Isolation**: Every document in `/channels/{channelId}`, `/messages/{messageId}`, and `/bridge_profiles/{userId}` belongs strictly to a single authenticated user (`userId == request.auth.uid`). No unauthenticated reads or writes are permitted.
2. **Relational Parent Consistency**: A `Message` in `/messages/{messageId}` can only be created if its `channelId` is a valid ID referencing an existing document `/channels/$(incoming().channelId)` owned by `request.auth.uid`.
3. **Immutable Identity & Creation Timestamps**: `userId`, `channelId` (on messages), and `createdAt` cannot be mutated during `update` operations.
4. **Strict Key & Size Boundaries**: Every write enforces `hasAll` and `hasOnly` on document keys, string length bounds, enum allowlists, and `<= request.time` timestamp validation.

## 2. The "Dirty Dozen" Payloads
1. **Unauthenticated Read/Write**: Caller with `request.auth == null` attempts `get`, `list`, `create`, `update`, or `delete` on `/channels`, `/messages`, or `/bridge_profiles`.
2. **Cross-User Spoofing on Create**: User `alice` creates a channel with `userId: "bob"`.
3. **Cross-User Read/List Scraping**: User `alice` queries `/channels` without `.where("userId", "==", "alice")` or tries to read Bob's channel.
4. **Orphaned Message Write**: User `alice` creates a `/messages/{id}` pointing to a non-existent `channelId` or a `channelId` owned by `bob`.
5. **Shadow / Ghost Field Injection**: User `alice` sends an extra field `isAdmin: true` or `isVerified: true` on `create` or `update`.
6. **Immutable Field Mutation**: User `alice` updates `createdAt`, `userId`, or `channelId` on an existing document.
7. **Future Timestamp Injection**: User `alice` writes `createdAt` or `updatedAt` in the future (`> request.time`).
8. **Enum Poisoning**: User `alice` sets `category: "HACKED"` on a channel or `messageType: "EXE_ROOTKIT"` on a message.
9. **String Overflow (Denial of Wallet)**: User `alice` writes a 10,000-character channel `name` or 50,000-character message `text`.
10. **Negative Counter Injection**: User `alice` writes `unreadCount: -5` or `reactionsCount: -1`.
11. **Path ID Poisoning**: User `alice` uses an invalid document ID containing spaces or special characters outside `^[a-zA-Z0-9_\-]+$`.
12. **Profile Impersonation**: User `alice` attempts to create or update `/bridge_profiles/bob_456`.
