package com.malgra.reliquary.core.data.library

import com.malgra.reliquary.core.model.LoadError
import com.malgra.reliquary.core.network.Failure
import com.malgra.reliquary.core.network.toFailure

fun Throwable.toLoadError(): LoadError = when (toFailure()) {
    Failure.UnknownHost, Failure.ConnectionRefused, Failure.Timeout, is Failure.Io -> LoadError.Offline
    is Failure.Http, is Failure.Tls, Failure.UnexpectedResponse -> LoadError.ServerError
    Failure.NoSessionCookie, null -> LoadError.Unexpected
}
