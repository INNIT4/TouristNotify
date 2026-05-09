# Firestore Collections — Source of Truth

Use `FirestoreCollections.*` constants (defined in `AppConstants.kt`) for all collection references. Never use raw strings.

## Collection Map

| Constant | Collection path | Purpose |
|---|---|---|
| `FirestoreCollections.USERS` | `users` | User profiles |
| `FirestoreCollections.USER_FAVORITES` | `favorites` | Subcollection under `users/{uid}` |
| `FirestoreCollections.CHECK_INS` | `checkIns` | Check-in records |
| `FirestoreCollections.EVENTS` | `eventos` | Tourist events |
| `FirestoreCollections.BLOG_POSTS` | `blog_posts` | Blog posts |
| `FirestoreCollections.TOURIST_SPOTS` | `lugares_turisticos` | Places (used by ProximityNotificationManager) |
| — | `lugares` | Places (used by MapsActivity, CheckInManager) |

## Known Inconsistency

`MapsActivity` and `CheckInManager` use `"lugares"`. `ProximityNotificationManager` uses `"lugares_turisticos"`. Both collections exist in production — do not merge without verifying data parity.

## User Document Structure

```
users/{userId}/
  ├── email: String
  ├── name: String
  ├── createdAt: Timestamp
  ├── favorites/ (subcollection)
  │     └── {placeId}/
  │           ├── placeId: String
  │           ├── placeName: String
  │           └── placeCategory: String
  └── stats/ (subcollection)
        └── summary/
              ├── totalCheckIns: Int
              ├── categoriesExplored: Map<String, Int>
              └── lastActivity: Timestamp
```

## Security Rules Summary

- `lugares_turisticos`: read = public; write = admin custom claim only
- `users/{uid}/**`: read/write = authenticated + uid matches
- `checkIns`: read = owner; write = authenticated
- `blog_posts`, `eventos`: read = public; write = admin
