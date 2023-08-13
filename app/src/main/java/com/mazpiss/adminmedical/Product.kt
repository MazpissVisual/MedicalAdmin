package com.mazpiss.adminmedical

data class Product(
    val id: String,
    val name: String,
    val price: Int,
    val description: String? = null,
    val rules: String? = null,
    val images: List<String>
)
