# STYLISTA — 5-MINUTE EXAM DEMO CHECKLIST & SCRIPT

**Project**: STYLISTA Android Application  
**Target Date**: Monday Exam Demo  
**Architecture**: Pure Kotlin, XML Views, Room Database v7, Firebase Auth & Cloud Firestore Dual-Write, Material 3 DayNight, Dual Map Architecture (Google Maps + OSM Fallback)  
**APK Path**: `app/build/outputs/apk/debug/app-debug.apk`  
**Test Account**: `demo.stylista@gmail.com` | Password: `StylistaPass123!` (or tap **Continue as Guest**)  

---

## Pre-Demo Setup (Do this before entering the exam room)

1. **Install and verify the APK on your device or emulator**:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. **Launch once to ensure session is logged in**:
   - If not logged in, log in with `demo.stylista@gmail.com` / `StylistaPass123!`.
   - `SplashActivity.kt` detects the active Firebase session and **auto-routes directly to Home** in ~1s with a smooth fade.
3. **Verify Display Settings**:
   - Both **Light Mode** and **Dark Mode** are supported. Start in **Light Mode** to showcase the warm butter-yellow/cream palette.
4. **Internet connection**:
   - Works fully offline for browsing, local asset images, cart, wishlist, checkout, and AI heuristic styling. An internet connection connects live Firebase Firestore sync and online AI styling.

---

## 5-Minute Step-by-Step Presentation Script for Panel

### ⏱️ 0:00 – 0:45 | Welcome Screen & Dynamic Style Onboarding
- **Action**: Tap the **Stylista** app icon on the home screen.
- **Talking Points**:
  - *"Welcome to Stylista. Notice our revamped welcome screen: a single high-resolution editorial fashion photo with responsive scrim gradients and vector brand mark."*
  - *"For new users, we have a style onboarding flow where users select their aesthetics, sizing preferences, and budget. These preferences are strictly scoped per user ID and ground our recommendation engine and AI chatbot."*
  - *"Since I have an active session (`demo.stylista@gmail.com`), the app auto-routes straight into the Home screen."*

---

### ⏱️ 0:45 – 1:30 | Home Feed, Dynamic Catalog & Real Photography
- **Action**: In Home, scroll through the hero banner, category chips, and product feeds.
- **Talking Points**:
  - *"Notice the clean fashion aesthetic—no clutter or generic styling. Every single one of our 32 products displays its own real, high-resolution WebP photograph from Unsplash under commercial license."*
  - *"Notice also that we do not show any fake ratings or fabricated review counts. If real review data doesn't exist, we don't invent it."*
- **Action**: Tap the top **Notifications** bell icon.
- **Talking Points**:
  - *"Our notifications are dynamic: they reflect the user's real placed orders, live delivery tracking updates, and active store promo codes (`WELCOME50`), rather than fake marketing text."*

---

### ⏱️ 1:30 – 2:30 | Product Sizing, Dynamic Delivery Threshold & Cart
- **Action**: Tap on any product (e.g., **Floral Summer Sundress** or **Classic Casual Denim Jacket**).
- **Talking Points**:
  - *"In Product Details, sizes adapt dynamically: apparel shows real in-stock sizes (`S`, `M`, `L`, `XL`), while accessories adapt to 'One Size'."*
- **Action**: Select size **M** and tap **Add to Bag**. The Cart screen opens.
- **Talking Points**:
  - *"Notice the dynamic delivery threshold banner at the top of the Cart: 'Add ₹X more for FREE Delivery!' This is calculated dynamically against our `StoreConfig` policy (Free delivery above ₹1,499; otherwise ₹99)."*
- **Action**: Enter promo code `WELCOME50` and tap **Apply**.
- **Talking Points**:
  - *"Applying `WELCOME50` deducts ₹50 honestly from the subtotal. The price breakdown updates dynamically."*

---

### ⏱️ 2:30 – 3:30 | Saved Address Book & Honest Checkout
- **Action**: Tap **Proceed to Checkout**.
- **Talking Points**:
  - *"On the Checkout screen, we see our saved delivery address card. Tapping 'Change' opens our full Address Book (`SavedAddressActivity`)."*
- **Action**: Tap **Change ›** on the Address Card to show the Address Book, then tap **+ Add New Address**.
- **Talking Points**:
  - *"Our address book supports full CRUD backed by Room v6 and Cloud Firestore dual-write under `users/{uid}/addresses`. It validates 10-digit Indian mobile numbers and 6-digit postal PIN codes."*
- **Action**: Return to Checkout. Point out the payment methods: **Cash on Delivery** and **Razorpay (Test Mode)**.
- **Talking Points**:
  - *"We removed all fake credit card and demo UPI forms. Checkout offers real Cash on Delivery or Razorpay test mode."*
- **Action**: Tap **Place Order (Cash on Delivery)**.
- **Talking Points**:
  - *"Placing the order executes an atomic dual-write to Room v7 and Cloud Firestore `users/{uid}/orders/{orderNumber}`. Notice that the cart is cleared strictly after the order successfully commits."*

---

### ⏱️ 3:30 – 4:15 | Order Confirmation & Live Tracking
- **Action**: The **Order Confirmed** screen (`OrderSuccessActivity`) appears with a green checkmark, copyable Order ID, estimated delivery date (4 days out), and payment summary.
- **Action**: Tap **Track Order**.
- **Talking Points**:
  - *"This opens `TrackOrderActivity`, featuring a 6-step vertical delivery timeline: Placed, Confirmed, Packed, Shipped, Out for Delivery, and Delivered."*
  - *"Notice the 'Live Sync' indicator at the top. The activity attaches a Firestore real-time snapshot listener directly to the order document. If an administrator updates the order status in the Firebase Console during grading, this screen updates in real time with zero manual refresh."*
  - *"Notice also our honest logistics note: 'Stylista Campus Express'—no fake courier names or fake tracking numbers."*

---

### ⏱️ 4:15 – 5:00 | My Orders, Honest Cancellation & 7-Day Exchange
- **Action**: Tap Back, then tap **View Order Details**.
- **Talking Points**:
  - *"In `OrderDetailsActivity`, we have the complete order breakdown: items, delivery address, payment info, and status history."*
- **Action**: Tap **Cancel Order**. The Cancel Order bottom sheet opens.
- **Talking Points**:
  - *"Cancellation is guarded by our state machine: it is permitted only during preparation (Placed, Confirmed, Packed). The bottom sheet honestly informs the customer that 'Nothing was charged for this order' before confirming."*
- **Action**: Confirm cancellation.
- **Talking Points**:
  - *"The order status transitions to CANCELLED in both Room and Firestore, and the cancel button disappears."*
  - *"For delivered orders, our 7-Day Exchange flow allows customers to choose an item, pick an in-stock replacement size, and log an exchange request with free courier pickup."*
- **Action**: Open Profile -> toggle between Light and Dark mode to demonstrate full theme compliance.
- **Talking Points**:
  - *"All screens comply with WCAG high-contrast standards across Light and Dark themes. Thank you!"*
