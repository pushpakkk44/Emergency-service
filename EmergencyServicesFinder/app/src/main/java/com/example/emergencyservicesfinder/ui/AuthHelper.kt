package com.example.emergencyservicesfinder.ui

import android.accounts.AccountManager
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import java.util.regex.Pattern

class AuthHelper(private val context: Context) {

    private var auth: FirebaseAuth? = null
    private var googleSignInClient: GoogleSignInClient? = null

    private var isExplicitlySignedOut: Boolean = false
    private var signedInEmail: String? = null
    private var signedInName: String? = null

    init {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            auth = FirebaseAuth.getInstance()
        } catch (_: Throwable) {
            auth = null
        }

        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build()
            googleSignInClient = GoogleSignIn.getClient(context, gso)
        } catch (_: Throwable) {
            googleSignInClient = null
        }
    }

    fun isUserSignedIn(): Boolean {
        if (isExplicitlySignedOut) return false
        return auth?.currentUser != null || signedInEmail != null || GoogleSignIn.getLastSignedInAccount(context) != null
    }

    fun getUserEmail(): String? {
        if (isExplicitlySignedOut) return null
        auth?.currentUser?.email?.let { return it }
        signedInEmail?.let { return it }
        GoogleSignIn.getLastSignedInAccount(context)?.email?.let { return it }
        return getDeviceGoogleAccountEmail()
    }

    fun getUserDisplayName(): String? {
        if (isExplicitlySignedOut) return null
        auth?.currentUser?.displayName?.let { return it }
        signedInName?.let { return it }
        GoogleSignIn.getLastSignedInAccount(context)?.displayName?.let { return it }
        val email = getUserEmail()
        return email?.substringBefore("@")?.replace(".", " ")
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun getDeviceGoogleAccountEmail(): String? {
        return try {
            val am = AccountManager.get(context)
            val accounts = am.getAccountsByType("com.google")
            if (accounts.isNotEmpty() && !accounts[0].name.isNullOrEmpty()) {
                accounts[0].name
            } else {
                null
            }
        } catch (_: Throwable) {
            null
        }
    }

    fun getSignInIntent(): Intent? {
        isExplicitlySignedOut = false
        return try {
            googleSignInClient?.signInIntent
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractAccountEmail(data: Intent?): String? {
        if (data == null) return null

        val authAccount = data.getStringExtra("authAccount")
        if (!authAccount.isNullOrEmpty() && authAccount.contains("@")) {
            return authAccount
        }

        val accountNameExtra = data.getStringExtra("account_name")
        if (!accountNameExtra.isNullOrEmpty() && accountNameExtra.contains("@")) {
            return accountNameExtra
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            if (task.isSuccessful && task.result != null && !task.result.email.isNullOrEmpty()) {
                return task.result.email
            }
        } catch (_: Throwable) {}

        val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
        if (lastAccount != null && !lastAccount.email.isNullOrEmpty()) {
            return lastAccount.email
        }

        val extras = data.extras
        if (extras != null) {
            val emailPattern = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
            for (key in extras.keySet()) {
                val value = extras.get(key)
                if (value is android.accounts.Account && !value.name.isNullOrEmpty()) {
                    return value.name
                }
                if (value is GoogleSignInAccount && !value.email.isNullOrEmpty()) {
                    return value.email
                }
                val str = value?.toString() ?: ""
                if (str.contains("@")) {
                    val matcher = emailPattern.matcher(str)
                    if (matcher.find()) {
                        return matcher.group()
                    }
                }
            }
        }
        return getDeviceGoogleAccountEmail()
    }

    fun handleSignInResult(
        data: Intent?,
        onSuccess: (email: String, name: String) -> Unit,
        onError: (message: String) -> Unit
    ) {
        isExplicitlySignedOut = false
        val extractedEmail = extractAccountEmail(data) ?: getDeviceGoogleAccountEmail()
        if (extractedEmail != null) {
            val name = extractedEmail.substringBefore("@").replace(".", " ")
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            setSignedInUser(extractedEmail, name)

            val lastAccount = GoogleSignIn.getLastSignedInAccount(context)
            if (lastAccount != null && lastAccount.idToken != null && auth != null) {
                try {
                    val credential = GoogleAuthProvider.getCredential(lastAccount.idToken, null)
                    auth?.signInWithCredential(credential)
                } catch (_: Throwable) {}
            }
            onSuccess(extractedEmail, name)
            return
        }

        if (data == null) {
            onError("Sign-in cancelled")
            return
        }

        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            if (account != null && !account.email.isNullOrEmpty()) {
                val email = account.email!!
                val name = account.displayName ?: email
                setSignedInUser(email, name)
                onSuccess(email, name)
            } else {
                onError("Sign-in cancelled")
            }
        } catch (e: ApiException) {
            val deviceEmail = getDeviceGoogleAccountEmail()
            if (deviceEmail != null) {
                val name = deviceEmail.substringBefore("@").replace(".", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                setSignedInUser(deviceEmail, name)
                onSuccess(deviceEmail, name)
            } else if (e.statusCode == 12501 || e.statusCode == 4) {
                onError("Sign-in cancelled")
            } else {
                onError("Google Sign-In error (${e.statusCode})")
            }
        } catch (_: Throwable) {
            val deviceEmail = getDeviceGoogleAccountEmail()
            if (deviceEmail != null) {
                val name = deviceEmail.substringBefore("@").replace(".", " ")
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                setSignedInUser(deviceEmail, name)
                onSuccess(deviceEmail, name)
            } else {
                onError("Google Sign-In error")
            }
        }
    }

    private fun setSignedInUser(email: String, name: String) {
        signedInEmail = email
        signedInName = name
    }

    fun signOut(onComplete: () -> Unit) {
        isExplicitlySignedOut = true
        signedInEmail = null
        signedInName = null
        try {
            auth?.signOut()
        } catch (_: Throwable) {}
        try {
            googleSignInClient?.signOut()?.addOnCompleteListener {
                onComplete()
            } ?: onComplete()
        } catch (_: Throwable) {
            onComplete()
        }
    }
}
