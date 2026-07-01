package ru.course.apitesting.provider.kitqat.model

import kotlinx.serialization.Serializable

@Serializable
data class KitqatPoolRequest(
    val templateName: String,
    val count: Int,
    val deleteFromPool: Boolean
)

@Serializable
data class CreditAccountsKitqatPoolResponse(
    val data: List<CreditAccountsKitqatData>
)

@Serializable
data class CreditAccountsKitqatData(
    val client: CreditAccountsKitqatClientData
)

@Serializable
data class CreditAccountsKitqatClientData(
    val siebelId: String,
    val phone: String,
    val products: Products = Products(),
)

@Serializable
data class Products(
    val debitCards: List<DebitCard>? = null,
    val cashLoans: List<CashLoan>? = null,
    val creditLines: List<CreditLine>? = null,
    val pos: List<Pos>? = null,
    val sharedPos: List<SharedProduct>? = null,
    val sharedCredits: List<SharedProduct>? = null,
    val studentLoans: List<StudentLoan>? = null,
    val sharedStudentLoans: List<SharedProduct>? = null,
    val sharedDebits: List<SharedProduct>? = null,
    val creditCards: List<CreditCard>? = null,
    val sharedCashLoans: List<SharedProduct>? = null,
)

@Serializable
data class CashLoan(
    val contractNumber: String?,
)

@Serializable
data class CreditLine(
    val contractNumber: String?,
)

@Serializable
data class DebitCard(
    val productName: String,
    val contractNumber: String,
    val card: Card
)

@Serializable
data class Card(
    val cardNum: String? = null,
    val ucid: String? = null,
    val cvv: String? = null,
    val expdate: String? = null,
)

@Serializable
data class Pos(
    val productName: String,
    val contractNumber: String,
    val loanAccount: DebitCard,
)

@Serializable
data class SharedProduct(
    val roleId: String,
    val status: String,
    val accountType: String,
    val scopes: List<String>,
    val owner: CreditAccountsKitqatClientData?,
    val recipient: CreditAccountsKitqatClientData?
)

@Serializable
data class StudentLoan(
    val productName: String,
    val creditContractNumber: String?,
    val currentContractNumber: String?
)

@Serializable
data class CreditCard(
    val productName: String,
    val contractNumber: String?,
    val card: Card?
)