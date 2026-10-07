# Shop Manager – Cloth & Antique Jewellery (Android)

Offline Android app (Kotlin + Jetpack Compose + Room). All data is stored on the phone.

## Tiles

**1. Sales**
- Search an item by name/barcode or scan its barcode → it goes into the bill.
- Item not in stock? Tap "Enter manually" and type the name, price and qty. Manual items are billed but don't touch stock.
- Tap a price in the bill to edit it. You can also add a discount.
- **Payment** → UPI / Cash / UPI + Cash. UPI shows a QR for the exact amount (set your UPI ID in Settings). Cash shows the change to return.
- On "Payment received" the bill is saved and **stock is reduced** in a single transaction. If any item is short, nothing is saved.
- History icon → today's total (UPI/Cash split) and all past bills.

**2. Stock**
- Add stock: scan a barcode or type a barcode/name, then enter the qty.
- **Same barcode (or same name without a barcode) → quantity is added to the existing item.** New barcode → new item.
- "Save & add next" lets you scan many items quickly.
- Search/scan, filter (Cloth / Jewellery / Low stock ≤ 2), tap an item to edit, correct its count or delete it.

**3. Purchase Payments**
- Record supplier bills: supplier, bill no, purchase date, due date (quick 7/15/30/45/60 days), amount, paid now.
- Summary: total pending, overdue and due in 7 days. Each bill shows Overdue / Due soon / Upcoming / Paid.
- Tap a bill → payment history and **Add payment** (part or full).

## Validations
- Sale: cart can't be empty, price > 0, qty can't go above stock, discount ≤ sub total, mobile must be 10 digits starting 6–9 (optional field), cash received ≥ bill, split UPI amount must be between 0 and the total.
- Stock: name required, selling price > 0, qty from 1 to 100000, barcode is unique, a name can't be reused by a different barcode, warning when selling price < cost.
- Purchases: supplier required, amount > 0, paid ≤ bill amount, purchase date not in the future, due date ≥ purchase date, payment ≤ pending, payment date between purchase date and today.
- Settings: UPI ID format check (e.g. `shopname@okaxis`).

## Build
1. Open the `ShopManager` folder in **Android Studio** (Ladybug or newer) and let Gradle sync.
2. Run on a phone (Android 8.0+), or use **Build → Build APK(s)**.
3. Barcode scanning uses Google's code scanner (Play services). It needs no camera permission and downloads its module on first use.

Or push to GitHub: `.github/workflows/build.yml` builds a debug APK and attaches it to every workflow run.
