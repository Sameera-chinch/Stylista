package com.example.stylista.data.repository

import android.content.Context
import android.util.Log
import com.example.stylista.database.AddressEntity
import com.example.stylista.database.CartEntity
import com.example.stylista.database.OrderEntity
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.utils.CartRepository
import com.example.stylista.utils.StoreConfig
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class OrderItemModel(
    val productId: Int,
    val name: String,
    val price: Int,
    val size: String,
    val quantity: Int,
    val imageUrl: String
)

data class StatusHistoryModel(
    val status: String,
    val timestamp: Long,
    val note: String
)

data class OrderDetailsModel(
    val orderNumber: String,
    val userId: String,
    val items: List<OrderItemModel>,
    val subtotal: Int,
    val deliveryFee: Int,
    val discountAmount: Int,
    val totalAmount: Int,
    val paymentMethod: String,
    val paymentStatus: String,
    val razorpayPaymentId: String,
    val deliveryAddress: AddressEntity?,
    val status: String,
    val statusHistory: List<StatusHistoryModel>,
    val createdAt: Long,
    val estimatedDeliveryDate: Long
)

object OrderRepository {

    private const val TAG = "OrderRepository"

    suspend fun createOrder(
        context: Context,
        userId: String,
        cartItems: List<CartEntity>,
        address: AddressEntity,
        paymentMethod: String, // "Cash on Delivery" or "Razorpay (Test Mode)"
        paymentStatus: String = if (paymentMethod.contains("Razorpay", ignoreCase = true)) "PAID" else "PENDING",
        discountAmount: Int = 0,
        razorpayPaymentId: String = ""
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (cartItems.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("Cart is empty"))
            }

            val db = StylistaDatabase.getInstance(context)
            val orderDao = db.orderDao()

            // 1. Build Item Models with rich product data
            val orderItems = cartItems.map { cartItem ->
                val product = db.productDao().getProductById(cartItem.productId)
                OrderItemModel(
                    productId = cartItem.productId,
                    name = product?.name ?: "Stylista Product",
                    price = product?.price ?: 0,
                    size = cartItem.size,
                    quantity = cartItem.quantity,
                    imageUrl = product?.imageUrl ?: ""
                )
            }

            val subtotal = orderItems.sumOf { it.price * it.quantity }
            val deliveryFee = StoreConfig.calculateDeliveryFee(subtotal)
            val totalAmount = maxOf(0, subtotal + deliveryFee - discountAmount)

            val now = System.currentTimeMillis()
            val orderNumber = "ORD-$now-${(1000..9999).random()}"
            val estimatedDelivery = now + 4 * 24 * 60 * 60 * 1000L // 4 days delivery window

            val initialStatus = "PLACED"
            val initialHistory = listOf(
                StatusHistoryModel(
                    status = initialStatus,
                    timestamp = now,
                    note = "Order placed successfully"
                )
            )

            val itemsJson = serializeItems(orderItems)
            val addressJson = serializeAddress(address)
            val historyJson = serializeStatusHistory(initialHistory)

            // 2. Insert into local Room database
            val orderEntity = OrderEntity(
                orderNumber = orderNumber,
                totalAmount = totalAmount,
                paymentMethod = paymentMethod,
                timestamp = now,
                userId = userId,
                itemsJson = itemsJson,
                addressJson = addressJson,
                status = initialStatus,
                statusHistoryJson = historyJson,
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                discountAmount = discountAmount,
                estimatedDeliveryDate = estimatedDelivery,
                paymentStatus = paymentStatus,
                razorpayPaymentId = razorpayPaymentId
            )
            orderDao.insertOrder(orderEntity)

            // 3. Write atomic order and order summary to Firestore:
            //    a) Root 'orders/{orderNumber}' collection (Visible immediately on Firebase Console main dashboard)
            //    b) Nested 'users/{uid}/orders/{orderNumber}' subcollection
            //    c) Merge 'orderSummary' and 'lastOrder' directly into 'users/{uid}' document
            try {
                val firestore = FirebaseFirestore.getInstance()
                val firestoreMap = buildFirestoreOrderMap(
                    orderNumber = orderNumber,
                    userId = userId,
                    orderItems = orderItems,
                    subtotal = subtotal,
                    deliveryFee = deliveryFee,
                    discountAmount = discountAmount,
                    totalAmount = totalAmount,
                    paymentMethod = paymentMethod,
                    paymentStatus = paymentStatus,
                    razorpayPaymentId = razorpayPaymentId,
                    address = address,
                    status = initialStatus,
                    statusHistory = initialHistory,
                    createdAt = now,
                    estimatedDeliveryDate = estimatedDelivery
                )

                // 1. Root /orders/{orderNumber}
                try {
                    firestore.collection("orders")
                        .document(orderNumber)
                        .set(firestoreMap)
                        .await()
                    Log.d(TAG, "Order $orderNumber written to root /orders collection")
                } catch (e: Exception) {
                    Log.w(TAG, "Root /orders write error: ${e.message}")
                }

                // 2. User-scoped /users/{userId}/orders/{orderNumber}
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("orders")
                        .document(orderNumber)
                        .set(firestoreMap)
                        .await()
                    Log.d(TAG, "Order $orderNumber written to users/$userId/orders")
                } catch (e: Exception) {
                    Log.w(TAG, "User /orders write error: ${e.message}")
                }

                // 3. User document /users/{userId} summary fields
                try {
                    @Suppress("UNCHECKED_CAST")
                    val orderSummaryMap = firestoreMap["orderSummary"] as? Map<String, Any> ?: emptyMap()
                    val userSummaryUpdate = hashMapOf<String, Any>(
                        "orderSummary" to orderSummaryMap,
                        "lastOrder" to firestoreMap,
                        "lastOrderNumber" to orderNumber,
                        "lastOrderAt" to now
                    )
                    firestore.collection("users")
                        .document(userId)
                        .set(userSummaryUpdate, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                    Log.d(TAG, "Order summary merged into users/$userId document")
                } catch (e: Exception) {
                    Log.w(TAG, "User orderSummary update error: ${e.message}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore write warning: ${e.message}")
            }

            // 4. Clear cart only after successful order placement
            CartRepository.clearUserCart(context, userId)

            Result.success(orderNumber)
        } catch (e: Exception) {
            Log.e(TAG, "Order creation failed", e)
            Result.failure(e)
        }
    }

    suspend fun getOrders(context: Context, userId: String): List<OrderDetailsModel> = withContext(Dispatchers.IO) {
        val db = StylistaDatabase.getInstance(context)
        val list = mutableListOf<OrderDetailsModel>()

        // 1. Try Firestore first
        try {
            val snapshot = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("orders")
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            for (doc in snapshot.documents) {
                val order = parseFirestoreDoc(doc.data ?: emptyMap(), userId)
                if (order != null) list.add(order)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore getOrders warning: ${e.message}")
        }

        // 2. Fallback to Room if Firestore returned empty or failed
        if (list.isEmpty()) {
            val localOrders = db.orderDao().getAllOrders(userId)
            for (local in localOrders) {
                list.add(fromEntity(local))
            }
        }

        list
    }

    suspend fun getOrderDetails(context: Context, userId: String, orderNumber: String): OrderDetailsModel? = withContext(Dispatchers.IO) {
        try {
            val doc = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("orders")
                .document(orderNumber)
                .get()
                .await()

            if (doc.exists()) {
                val parsed = parseFirestoreDoc(doc.data ?: emptyMap(), userId)
                if (parsed != null) return@withContext parsed
            }
        } catch (e: Exception) {
            Log.w(TAG, "Firestore getOrderDetails warning: ${e.message}")
        }

        // Fallback to local Room
        val local = StylistaDatabase.getInstance(context).orderDao().getOrderByOrderNumber(orderNumber)
        local?.let { fromEntity(it) }
    }

    suspend fun cancelOrder(context: Context, userId: String, orderNumber: String, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val db = StylistaDatabase.getInstance(context)

            // Update Room
            val local = db.orderDao().getOrderByOrderNumber(orderNumber)
            if (local != null) {
                val history = deserializeStatusHistory(local.statusHistoryJson).toMutableList()
                history.add(StatusHistoryModel("CANCELLED", now, "Order cancelled: $reason"))
                val updated = local.copy(
                    status = "CANCELLED",
                    statusHistoryJson = serializeStatusHistory(history)
                )
                db.orderDao().updateOrder(updated)
            }

            // Update Firestore
            try {
                val docRef = FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(userId)
                    .collection("orders")
                    .document(orderNumber)

                val doc = docRef.get().await()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val existingHistory = (doc.get("statusHistory") as? List<Map<String, Any>> ?: emptyList()).toMutableList()
                    existingHistory.add(mapOf("status" to "CANCELLED", "timestamp" to now, "note" to "Order cancelled: $reason"))
                    docRef.update(mapOf("status" to "CANCELLED", "statusHistory" to existingHistory)).await()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firestore cancelOrder warning: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun requestExchange(
        context: Context,
        userId: String,
        orderNumber: String,
        productId: Int,
        productName: String,
        oldSize: String,
        newSize: String,
        reason: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val exchangeId = "EXCH-${System.currentTimeMillis()}"
            val exchangeData = hashMapOf(
                "exchangeId" to exchangeId,
                "orderNumber" to orderNumber,
                "productId" to productId,
                "productName" to productName,
                "oldSize" to oldSize,
                "newSize" to newSize,
                "reason" to reason,
                "status" to "REQUESTED",
                "requestedAt" to System.currentTimeMillis()
            )

            FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .collection("orders")
                .document(orderNumber)
                .collection("exchanges")
                .document(exchangeId)
                .set(exchangeData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Request exchange failed", e)
            Result.failure(e)
        }
    }

    // --- JSON Serialization Helpers ---

    private fun serializeItems(items: List<OrderItemModel>): String {
        val array = JSONArray()
        for (item in items) {
            val obj = JSONObject().apply {
                put("productId", item.productId)
                put("name", item.name)
                put("price", item.price)
                put("size", item.size)
                put("quantity", item.quantity)
                put("imageUrl", item.imageUrl)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeItems(json: String): List<OrderItemModel> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<OrderItemModel>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    OrderItemModel(
                        productId = obj.getInt("productId"),
                        name = obj.getString("name"),
                        price = obj.getInt("price"),
                        size = obj.getString("size"),
                        quantity = obj.getInt("quantity"),
                        imageUrl = obj.optString("imageUrl", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "deserializeItems failed", e)
        }
        return list
    }

    private fun serializeAddress(address: AddressEntity): String {
        return JSONObject().apply {
            put("id", address.id)
            put("userId", address.userId)
            put("name", address.name)
            put("phone", address.phone)
            put("pincode", address.pincode)
            put("houseFlat", address.houseFlat)
            put("streetArea", address.streetArea)
            put("landmark", address.landmark)
            put("city", address.city)
            put("state", address.state)
            put("type", address.type)
        }.toString()
    }

    fun deserializeAddress(json: String): AddressEntity? {
        if (json.isBlank()) return null
        return try {
            val obj = JSONObject(json)
            AddressEntity(
                id = obj.optString("id", "addr"),
                userId = obj.optString("userId", ""),
                name = obj.getString("name"),
                phone = obj.getString("phone"),
                pincode = obj.getString("pincode"),
                houseFlat = obj.getString("houseFlat"),
                streetArea = obj.getString("streetArea"),
                landmark = obj.optString("landmark", ""),
                city = obj.getString("city"),
                state = obj.getString("state"),
                type = obj.optString("type", "Home")
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun serializeStatusHistory(history: List<StatusHistoryModel>): String {
        val array = JSONArray()
        for (h in history) {
            val obj = JSONObject().apply {
                put("status", h.status)
                put("timestamp", h.timestamp)
                put("note", h.note)
            }
            array.put(obj)
        }
        return array.toString()
    }

    fun deserializeStatusHistory(json: String): List<StatusHistoryModel> {
        if (json.isBlank()) return emptyList()
        val list = mutableListOf<StatusHistoryModel>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    StatusHistoryModel(
                        status = obj.getString("status"),
                        timestamp = obj.getLong("timestamp"),
                        note = obj.optString("note", "")
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "deserializeStatusHistory failed", e)
        }
        return list
    }

    private fun fromEntity(entity: OrderEntity): OrderDetailsModel {
        val items = deserializeItems(entity.itemsJson)
        val address = deserializeAddress(entity.addressJson)
        val history = deserializeStatusHistory(entity.statusHistoryJson)
        return OrderDetailsModel(
            orderNumber = entity.orderNumber.ifBlank { "ORD-${entity.id}" },
            userId = entity.userId,
            items = items,
            subtotal = if (entity.subtotal > 0) entity.subtotal else entity.totalAmount,
            deliveryFee = entity.deliveryFee,
            discountAmount = entity.discountAmount,
            totalAmount = entity.totalAmount,
            paymentMethod = entity.paymentMethod,
            paymentStatus = entity.paymentStatus,
            razorpayPaymentId = entity.razorpayPaymentId,
            deliveryAddress = address,
            status = entity.status,
            statusHistory = history,
            createdAt = entity.timestamp,
            estimatedDeliveryDate = if (entity.estimatedDeliveryDate > 0) entity.estimatedDeliveryDate else entity.timestamp + 4 * 24 * 60 * 60 * 1000L
        )
    }

    fun parseFirestoreDoc(data: Map<String, Any>, userId: String): OrderDetailsModel? {
        return try {
            val orderNumber = data["orderId"] as? String ?: return null
            val total = (data["total"] as? Long)?.toInt() ?: 0
            val subtotal = (data["subtotal"] as? Long)?.toInt() ?: total
            val deliveryFee = (data["deliveryFee"] as? Long)?.toInt() ?: 0
            val discountAmount = (data["discountAmount"] as? Long)?.toInt() ?: 0
            val status = data["status"] as? String ?: "PLACED"
            val createdAt = (data["createdAt"] as? Long) ?: System.currentTimeMillis()
            val estimatedDelivery = (data["estimatedDeliveryDate"] as? Long) ?: (createdAt + 4 * 24 * 60 * 60 * 1000L)

            @Suppress("UNCHECKED_CAST")
            val paymentMap = data["payment"] as? Map<String, Any> ?: emptyMap()
            val paymentMethod = paymentMap["method"] as? String ?: "Cash on Delivery"
            val paymentStatus = paymentMap["status"] as? String ?: "PENDING"
            val razorpayId = paymentMap["razorpayPaymentId"] as? String ?: ""

            @Suppress("UNCHECKED_CAST")
            val rawItems = data["items"] as? List<Map<String, Any>> ?: emptyList()
            val items = rawItems.map { raw ->
                OrderItemModel(
                    productId = (raw["productId"] as? Long)?.toInt() ?: 0,
                    name = raw["name"] as? String ?: "Stylista Product",
                    price = (raw["price"] as? Long)?.toInt() ?: 0,
                    size = raw["size"] as? String ?: "Free Size",
                    quantity = (raw["quantity"] as? Long)?.toInt() ?: 1,
                    imageUrl = raw["imageUrl"] as? String ?: ""
                )
            }

            @Suppress("UNCHECKED_CAST")
            val rawAddress = data["deliveryAddress"] as? Map<String, Any> ?: emptyMap()
            val address = if (rawAddress.isNotEmpty()) {
                AddressEntity(
                    id = "addr",
                    userId = userId,
                    name = rawAddress["name"] as? String ?: "",
                    phone = rawAddress["phone"] as? String ?: "",
                    pincode = rawAddress["pincode"] as? String ?: "",
                    houseFlat = rawAddress["houseFlat"] as? String ?: "",
                    streetArea = rawAddress["streetArea"] as? String ?: "",
                    landmark = rawAddress["landmark"] as? String ?: "",
                    city = rawAddress["city"] as? String ?: "",
                    state = rawAddress["state"] as? String ?: "",
                    type = rawAddress["type"] as? String ?: "Home"
                )
            } else null

            @Suppress("UNCHECKED_CAST")
            val rawHistory = data["statusHistory"] as? List<Map<String, Any>> ?: emptyList()
            val history = rawHistory.map { h ->
                StatusHistoryModel(
                    status = h["status"] as? String ?: "PLACED",
                    timestamp = (h["timestamp"] as? Long) ?: createdAt,
                    note = h["note"] as? String ?: ""
                )
            }

            OrderDetailsModel(
                orderNumber = orderNumber,
                userId = userId,
                items = items,
                subtotal = subtotal,
                deliveryFee = deliveryFee,
                discountAmount = discountAmount,
                totalAmount = total,
                paymentMethod = paymentMethod,
                paymentStatus = paymentStatus,
                razorpayPaymentId = razorpayId,
                deliveryAddress = address,
                status = status,
                statusHistory = history,
                createdAt = createdAt,
                estimatedDeliveryDate = estimatedDelivery
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseFirestoreDoc error", e)
            null
        }
    }

    fun buildFirestoreOrderMap(
        orderNumber: String,
        userId: String,
        orderItems: List<OrderItemModel>,
        subtotal: Int,
        deliveryFee: Int,
        discountAmount: Int,
        totalAmount: Int,
        paymentMethod: String,
        paymentStatus: String,
        razorpayPaymentId: String,
        address: AddressEntity?,
        status: String,
        statusHistory: List<StatusHistoryModel>,
        createdAt: Long,
        estimatedDeliveryDate: Long
    ): HashMap<String, Any> {
        val orderSummaryMap = hashMapOf<String, Any>(
            "orderNumber" to orderNumber,
            "itemsCount" to orderItems.sumOf { it.quantity },
            "itemCount" to orderItems.sumOf { it.quantity },
            "subtotal" to subtotal,
            "deliveryFee" to deliveryFee,
            "discountAmount" to discountAmount,
            "discount" to discountAmount,
            "total" to totalAmount,
            "totalAmount" to totalAmount,
            "paymentMethod" to paymentMethod,
            "paymentStatus" to paymentStatus,
            "status" to status,
            "createdAt" to createdAt,
            "estimatedDeliveryDate" to estimatedDeliveryDate
        )

        val firestoreMap = hashMapOf<String, Any>(
            "orderId" to orderNumber,
            "orderNumber" to orderNumber,
            "userId" to userId,
            "items" to orderItems.map { item ->
                hashMapOf(
                    "productId" to item.productId,
                    "name" to item.name,
                    "price" to item.price,
                    "size" to item.size,
                    "quantity" to item.quantity,
                    "imageUrl" to item.imageUrl
                )
            },
            "subtotal" to subtotal,
            "deliveryFee" to deliveryFee,
            "discountAmount" to discountAmount,
            "total" to totalAmount,
            "totalAmount" to totalAmount,
            "orderSummary" to orderSummaryMap,
            "payment" to hashMapOf(
                "method" to paymentMethod,
                "status" to paymentStatus,
                "razorpayPaymentId" to razorpayPaymentId
            ),
            "status" to status,
            "statusHistory" to statusHistory.map { h ->
                hashMapOf(
                    "status" to h.status,
                    "timestamp" to h.timestamp,
                    "note" to h.note
                )
            },
            "createdAt" to createdAt,
            "estimatedDeliveryDate" to estimatedDeliveryDate
        )

        if (address != null) {
            firestoreMap["deliveryAddress"] = hashMapOf(
                "name" to address.name,
                "phone" to address.phone,
                "pincode" to address.pincode,
                "houseFlat" to address.houseFlat,
                "streetArea" to address.streetArea,
                "landmark" to address.landmark,
                "city" to address.city,
                "state" to address.state,
                "type" to address.type
            )
        }

        return firestoreMap
    }

    suspend fun syncAllOrdersToFirestore(context: Context, userId: String) = withContext(Dispatchers.IO) {
        try {
            val db = StylistaDatabase.getInstance(context)
            val localOrders = db.orderDao().getAllOrders(userId)
            if (localOrders.isEmpty()) return@withContext

            val firestore = FirebaseFirestore.getInstance()
            for (order in localOrders) {
                val model = fromEntity(order)
                val firestoreMap = buildFirestoreOrderMap(
                    orderNumber = model.orderNumber,
                    userId = model.userId,
                    orderItems = model.items,
                    subtotal = model.subtotal,
                    deliveryFee = model.deliveryFee,
                    discountAmount = model.discountAmount,
                    totalAmount = model.totalAmount,
                    paymentMethod = model.paymentMethod,
                    paymentStatus = model.paymentStatus,
                    razorpayPaymentId = model.razorpayPaymentId,
                    address = model.deliveryAddress,
                    status = model.status,
                    statusHistory = model.statusHistory,
                    createdAt = model.createdAt,
                    estimatedDeliveryDate = model.estimatedDeliveryDate
                )

                // 1. Root /orders/{orderNumber}
                try {
                    firestore.collection("orders")
                        .document(model.orderNumber)
                        .set(firestoreMap, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "syncAllOrders root /orders error: ${e.message}")
                }

                // 2. User /users/{userId}/orders/{orderNumber}
                try {
                    firestore.collection("users")
                        .document(userId)
                        .collection("orders")
                        .document(model.orderNumber)
                        .set(firestoreMap, com.google.firebase.firestore.SetOptions.merge())
                        .await()
                } catch (e: Exception) {
                    Log.w(TAG, "syncAllOrders user /orders error: ${e.message}")
                }
            }

            // 3. User document orderSummary & lastOrder
            val latest = localOrders.maxByOrNull { it.timestamp }
            if (latest != null) {
                val latestModel = fromEntity(latest)
                val summaryMap = hashMapOf<String, Any>(
                    "orderNumber" to latestModel.orderNumber,
                    "itemCount" to latestModel.items.sumOf { it.quantity },
                    "itemsCount" to latestModel.items.sumOf { it.quantity },
                    "subtotal" to latestModel.subtotal,
                    "deliveryFee" to latestModel.deliveryFee,
                    "discountAmount" to latestModel.discountAmount,
                    "discount" to latestModel.discountAmount,
                    "total" to latestModel.totalAmount,
                    "totalAmount" to latestModel.totalAmount,
                    "paymentMethod" to latestModel.paymentMethod,
                    "paymentStatus" to latestModel.paymentStatus,
                    "status" to latestModel.status,
                    "createdAt" to latestModel.createdAt,
                    "estimatedDeliveryDate" to latestModel.estimatedDeliveryDate
                )
                try {
                    firestore.collection("users")
                        .document(userId)
                        .set(
                            hashMapOf<String, Any>(
                                "orderSummary" to summaryMap,
                                "lastOrderNumber" to latestModel.orderNumber,
                                "lastOrderAt" to latestModel.createdAt
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                        .await()
                    Log.d(TAG, "Synced orderSummary into users/$userId document")
                } catch (e: Exception) {
                    Log.w(TAG, "syncAllOrders user orderSummary error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncAllOrdersToFirestore warning: ${e.message}")
        }
    }
}
