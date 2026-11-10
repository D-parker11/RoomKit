# Tenant and workflow acceptance checks

Base commit: 840888f93bd4f8688873ac2f59cd7ac726aa0a7a.

## Changes

- Tenant details now opens Edit tenant. Saves update the same tenant primary key and creation timestamp; no tenancy or invoice rows are rewritten.
- Create/edit duplicate checks use one shared normalization rule for Cambodian local/+855/00855 phone formats and trimmed case-insensitive identity documents. The DAO checks and write execute in one transaction. Existing records are not merged/deleted; a legacy duplicate may need its contact details corrected before editing can succeed.
- Tenant search includes ID/passport. Search and filter state are restored on return; empty search, empty filter and no-profiles states explain the result.
- Rooms use one RecyclerView with a header adapter instead of expanding all cards inside NestedScrollView. Saving sends the inserted room ID, clears search, selects All, and scrolls to the inserted card after list commit. The reported delay is not device-reproduced here; emulator verification remains required.
- Meter readings restores a month/year picker and warns that switching discards unsaved inputs. The existing ViewModel validates periods and repository retains saved-reading/closed-period/issued-invoice guards.
- Move-out always creates an itemized FINAL invoice, including zero charges, within the existing settlement transaction. It does not create a zero payment or carry previous debt into new charges. Closed periods still require reopening before final preparation. Existing historical settlements are not backfilled.
- Database schema stays at version 11; no data migration or destructive reset is introduced.

## Validation performed here

- All 196 resource XML files parsed successfully with Python ElementTree.
- `git diff --check` passed.
- Build/tests attempted with `bash ./gradlew :app:assembleDebug :app:testDebugUnitTest --console=plain`. Blocked before Gradle started: distribution download network unavailable. No Android compilation, JVM suite pass or device pass is claimed.
- Added shared Room regression cases (run by existing Robolectric/instrumentation wrappers) and TenantIdentity unit cases. These are added, not executed here.

## Run locally

Use a separate test emulator, not your demonstration data. From this branch:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Install with Android Studio Run. Instrumentation runs may uninstall test apps; use only a disposable emulator for those.

| Check | Expected | Result |
|---|---|---|
| Edit tenant name, phone, ID and notes; restart | Values retained; existing tenancy/invoices still linked | Not run |
| Save unchanged unique identity | Success | Not run |
| Duplicate phone in local/+855/00855 forms, create and edit | Blocked; no override or extra profile | Not run |
| Duplicate ID/passport with case/space changes | Blocked | Not run |
| Blank name/phone on edit | Blocked | Not run |
| Rotate while editing | Draft field values retained | Not run |
| Search name, phone, ID; unmatched search; clear search | Correct results and explanatory empty states | Not run |
| No profiles vs Active/Moved Out with no matches | Correct empty message and Add visibility | Not run |
| Add rooms 10–15, from All/Vacant/Occupied and with search | New card shown after save; count correct; no reopening required | Not run |
| Scroll 30+ cards; large font; rotate room list | Smooth recycling; header/search usable | Not run |
| Meter picker: previous/current month; cancel; rotate picker | Correct selection, cancellation retains inputs | Not run |
| Meter locked/saved/closed period | Selection does not bypass write guards | Not run |
| Zero new charges at move-out with existing debt and pending refund | FINAL invoice 0 KHR, no payment inserted, prior debt intact, refund retained, room vacant | Not run |
| Nonzero final charges and deposit allocation | Same correct calculations and allocation as before | Not run |
| Retry completed move-out | Rejected; no duplicate final invoice | Not run |
