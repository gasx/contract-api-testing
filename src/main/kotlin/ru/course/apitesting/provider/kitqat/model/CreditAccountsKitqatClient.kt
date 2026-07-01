package ru.course.apitesting.provider.kitqat.model

interface CreditAccountsKitqatClient {
    fun getDataFromPool(kitqatPoolRequest: KitqatPoolRequest): CreditAccountsKitqatPoolResponse
}