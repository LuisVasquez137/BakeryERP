package com.bakery.erp.data

import cocoapods.FirebaseFirestoreInternal.FIRFieldValue
import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FieldValueSerializer
import dev.gitlive.firebase.internal.decode

// Sin compilar: el código de iOS no se puede compilar desde Windows.
actual fun incrementBy(value: Double): FieldValue =
    decode(FieldValueSerializer, FIRFieldValue.fieldValueForDoubleIncrement(value))
