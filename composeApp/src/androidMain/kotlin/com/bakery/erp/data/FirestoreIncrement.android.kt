package com.bakery.erp.data

import dev.gitlive.firebase.firestore.FieldValue
import dev.gitlive.firebase.firestore.FieldValueSerializer
import dev.gitlive.firebase.internal.decode

// El constructor de FieldValue es interno de GitLive; su propio serializador envuelve el
// valor nativo sin tocarlo.
actual fun incrementBy(value: Double): FieldValue =
    decode(FieldValueSerializer, com.google.firebase.firestore.FieldValue.increment(value))
