package com.bakery.erp.data

import dev.gitlive.firebase.firestore.FieldValue

// En JS un Int y un Double son el mismo número: el `increment(n)` del SDK web acepta
// decimales, solo la firma de GitLive dice Int.
actual fun incrementBy(value: Double): FieldValue = FieldValue.increment(value.unsafeCast<Int>())
