package ru.course.apitesting.integration.kitqat

enum class KitqatProductType {
    DEBIT_CARD {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.debitCards
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    CREDIT_CARD {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.creditCards
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    CASH_LOAN {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.cashLoans
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    CREDIT_LINE {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.creditLines
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    POS {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.pos
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    STUDENT_LOAN {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.studentLoans
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    SHARED_POS {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.sharedPos
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    SHARED_CREDIT {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.sharedCredits
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    SHARED_DEBIT {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.sharedDebits
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    SHARED_CASH_LOAN {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.sharedCashLoans
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    },

    SHARED_STUDENT_LOAN {
        override fun select(
            client: CreditAccountsKitqatClientData,
            index: Int
        ): KitqatSelectedProductDto? {
            return client.products.sharedStudentLoans
                ?.getOrNull(index)
                ?.toSelected(name, index)
        }
    };

    abstract fun select(
        client: CreditAccountsKitqatClientData,
        index: Int
    ): KitqatSelectedProductDto?

    fun selectRequired(
        client: CreditAccountsKitqatClientData,
        index: Int
    ): KitqatSelectedProductDto {
        return select(client, index)
            ?: error("У клиента ${client.siebelId} не найден продукт $name с index=$index")
    }

    companion object {
        fun fromCode(code: String): KitqatProductType {
            return values().firstOrNull { it.name == code }
                ?: error("Неизвестный Kitqat product: $code. Доступные значения: ${values().joinToString { it.name }}")
        }
    }
}

private fun DebitCard.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        productName = productName,
        contractNumber = contractNumber,
        card = card
    )
}

private fun CreditCard.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        productName = productName,
        contractNumber = contractNumber,
        card = card
    )
}

private fun CashLoan.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        contractNumber = contractNumber
    )
}

private fun CreditLine.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        contractNumber = contractNumber
    )
}

private fun Pos.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        productName = productName,
        contractNumber = contractNumber,
        loanAccount = loanAccount
    )
}

private fun StudentLoan.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        productName = productName,
        creditContractNumber = creditContractNumber,
        currentContractNumber = currentContractNumber
    )
}

private fun SharedProduct.toSelected(
    type: String,
    index: Int
): KitqatSelectedProductDto {
    return KitqatSelectedProductDto(
        type = type,
        index = index,
        roleId = roleId,
        status = status,
        accountType = accountType,
        scopes = scopes,
        ownerSiebelId = owner?.siebelId,
        ownerPhone = owner?.phone,
        recipientSiebelId = recipient?.siebelId,
        recipientPhone = recipient?.phone
    )
}