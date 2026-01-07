package com.algerianidpassportreader.testapp

data class MrzInfoModel(
    val documentNumber: String,
    val dateOfBirth: String,
    val dateOfExpiry: String,
    val nationality: String? = "DZA",
    val gender: String? = "",
    val issuingState: String? = "DZA",
    val primaryIdentifier: String? = "",
    val secondaryIdentifier: String? = "",
    val documentCode: String? = "" // P or ID
) {

    companion object {
        // Convert from JSON (Map<String, Any>) to MrzInfoModel
        fun fromJson(json: Map<String, Any?>): MrzInfoModel {
            return MrzInfoModel(
                documentNumber = json["documentNumber"] as? String ?: "",
                dateOfBirth = json["dateOfBirth"] as? String ?: "",
                dateOfExpiry = json["dateOfExpiry"] as? String ?: "",
                gender = json["gender"] as? String ?: "",
                documentCode = json["documentCode"] as? String ?: "",
                issuingState = json["issuingState"] as? String ?: "DZA",
                nationality = json["nationality"] as? String ?: "DZA",
                primaryIdentifier = json["primaryIdentifier"] as? String ?: "",
                secondaryIdentifier = json["secondaryIdentifier"] as? String ?: ""
            )
        }
    }

    // Convert MrzInfoModel to JSON (Map<String, String>)
    fun toJson(): Map<String, String> {
        return mapOf(
            "documentNumber" to documentNumber,
            "dateOfBirth" to dateOfBirth,
            "dateOfExpiry" to dateOfExpiry,
            "gender" to (gender ?: ""),
            "documentCode" to (documentCode ?: ""),
            "issuingState" to (issuingState ?: ""),
            "nationality" to (nationality ?: ""),
            "primaryIdentifier" to (primaryIdentifier ?: ""),
            "secondaryIdentifier" to (secondaryIdentifier ?: "")
        )
    }
}
