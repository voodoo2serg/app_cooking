package ru.vkusdetstva.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DigitalBookBillingState(
    val connected: Boolean = false,
    val productAvailable: Boolean = false,
    val price: String = "",
    val unlocked: Boolean = false,
    val message: String = ""
)

class DigitalBookBilling(context: Context) : PurchasesUpdatedListener {
    companion object { const val PRODUCT_ID = "family_book_export" }

    private val _state = MutableStateFlow(DigitalBookBillingState())
    val state: StateFlow<DigitalBookBillingState> = _state.asStateFlow()
    private var productDetails: ProductDetails? = null

    private val client = BillingClient.newBuilder(context)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    init { connect() }

    private fun connect() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingServiceDisconnected() {
                _state.value = _state.value.copy(connected = false, message = "Связь с магазином временно недоступна.")
            }
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    _state.value = _state.value.copy(connected = true, message = "")
                    queryProduct()
                    queryPurchases()
                } else _state.value = _state.value.copy(message = result.debugMessage)
            }
        })
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )).build()
        client.queryProductDetailsAsync(params) { result, response ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetails = response.productDetailsList.firstOrNull()
                val offer = productDetails?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                _state.value = _state.value.copy(
                    productAvailable = productDetails != null,
                    price = offer?.formattedPrice.orEmpty(),
                    message = if (productDetails == null) "Товар family_book_export ещё не настроен в Play Console." else ""
                )
            }
        }
    }

    private fun queryPurchases() {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        ) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.forEach(::processPurchase)
        }
    }

    fun launchPurchase(activity: Activity) {
        val details = productDetails ?: run {
            _state.value = _state.value.copy(message = "Экспорт ещё не настроен в магазине.")
            return
        }
        val offerToken = details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken
        val item = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
            .apply { if (!offerToken.isNullOrBlank()) setOfferToken(offerToken) }
            .build()
        val result = client.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(item)).build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _state.value = _state.value.copy(message = result.debugMessage)
        }
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        if (result.responseCode == BillingClient.BillingResponseCode.OK) purchases.orEmpty().forEach(::processPurchase)
        else if (result.responseCode != BillingClient.BillingResponseCode.USER_CANCELED) {
            _state.value = _state.value.copy(message = result.debugMessage)
        }
    }

    private fun processPurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED || PRODUCT_ID !in purchase.products) return
        _state.value = _state.value.copy(unlocked = true, message = "Полный экспорт книги открыт.")
        if (!purchase.isAcknowledged) {
            client.acknowledgePurchase(
                AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            ) { }
        }
    }

    fun close() { if (client.isReady) client.endConnection() }
}
