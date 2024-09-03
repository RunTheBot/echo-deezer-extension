package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import java.util.Locale

data class DeezerCredentials(
    val arl: String,
    val sid: String,
    val token: String,
    val userId: String,
    val licenseToken: String,
    val email: String,
    val pass: String
)

object DeezerCountries {
    fun getDefaultCountryIndex(settings: Settings?): Int {
        val storedCountryCode = settings?.getString("countryCode")

        return if (storedCountryCode != null) {
            countryEntryValues.indexOf(storedCountryCode).takeIf { it >= 0 } ?: 0
        } else {
            val defaultCountryCode = Locale.getDefault().country
            settings?.putString("countryCode", defaultCountryCode)
            countryEntryValues.indexOf(defaultCountryCode).takeIf { it >= 0 } ?: 0
        }
    }

    val countryEntryTitles = mutableListOf(
        "Afghanistan", "Albania", "Algeria", "Angola", "Anguilla", "Antigua and Barbuda", "Argentina", "Armenia", "Australia",
        "Austria", "Azerbaijan", "Bahrain", "Bangladesh", "Barbados", "Belgium", "Benin", "Bhutan", "Bolivia", "Bosnia and Herzegovina",
        "Botswana", "Brazil", "British Indian Ocean Territory", "British Virgin Islands", "Brunei", "Bulgaria", "Burkina Faso",
        "Burundi", "Cambodia", "Cameroon", "Canada", "Cape Verde", "Cayman Islands", "Central African Republic", "Chad", "Chile",
        "Christmas Island", "Cocos Islands", "Colombia", "Cook Islands", "Costa Rica", "Croatia", "Cyprus", "Czech Republic",
        "Democratic Republic of the Congo", "Denmark", "Djibouti", "Dominica", "Dominican Republic", "East Timor", "Ecuador",
        "Egypt", "El Salvador", "Equatorial Guinea", "Eritrea", "Estonia", "Ethiopia", "Federated States of Micronesia", "Fiji",
        "Finland", "France", "Gabon", "Gambia", "Georgia", "Germany", "Ghana", "Greece", "Grenada", "Guatemala", "Guinea",
        "Guinea-Bissau", "Honduras", "Hungary", "Iceland", "Indonesia", "Iraq", "Ireland", "Israel", "Italy", "Jamaica", "Japan",
        "Jordan", "Kazakhstan", "Kenya", "Kiribati", "Kuwait", "Kyrgyzstan", "Laos", "Latvia", "Lebanon", "Lesotho", "Liberia",
        "Libya", "Lithuania", "Luxembourg", "Macedonia", "Madagascar", "Malawi", "Malaysia", "Mali", "Malta", "Marshall Islands",
        "Mauritania", "Mauritius", "Mexico", "Moldova", "Mongolia", "Montenegro", "Montserrat", "Morocco", "Mozambique", "Namibia",
        "Nauru", "Nepal", "New Zealand", "Nicaragua", "Niger", "Nigeria", "Niue", "Norfolk Island", "Norway", "Oman", "Pakistan",
        "Palau", "Panama", "Papua New Guinea", "Paraguay", "Peru", "Poland", "Portugal", "Qatar", "Republic of the Congo",
        "Romania", "Rwanda", "Saint Kitts and Nevis", "Saint Lucia", "Saint Vincent and the Grenadines", "Samoa",
        "São Tomé and Príncipe", "Saudi Arabia", "Senegal", "Serbia", "Seychelles", "Sierra Leone", "Singapore", "Slovakia",
        "Slovenia", "Somalia", "South Africa", "Spain", "Sri Lanka", "Svalbard and Jan Mayen", "Swaziland", "Sweden", "Switzerland",
        "Tajikistan", "Tanzania", "Thailand", "The Comoros", "the Falkland Islands", "The Ivory Coast", "the Maldives",
        "the Netherlands", "the Philippines", "the Pitcairn Islands", "the Solomon Islands", "Togo", "Tokelau", "Tonga",
        "Tunisia", "Turkey", "Turkmenistan", "Turks and Caicos Islands", "Tuvalu", "Uganda", "Ukraine", "United Arab Emirates",
        "United Kingdom", "United States of America", "Uruguay", "Uzbekistan", "Vanuatu", "Venezuela", "Vietnam", "Yemen", "Zambia",
        "Zimbabwe"
    )

    val countryEntryValues = mutableListOf(
        "AF", "AL", "DZ", "AO", "AI", "AG", "AR", "AM", "AU", "AT", "AZ", "BH", "BD", "BB", "BE", "BJ", "BT", "BO", "BA", "BW",
        "BR", "IO", "VG", "BN", "BG", "BF", "BI", "KH", "CM", "CA", "CV", "KY", "CF", "TD", "CL", "CX", "CC", "CO", "CK", "CR",
        "HR", "CY", "CZ", "CD", "DK", "DJ", "DM", "DO", "TL", "EC", "EG", "SV", "GQ", "ER", "EE", "ET", "FM", "FJ", "FI", "FR",
        "GA", "GM", "GE", "DE", "GH", "GR", "GD", "GT", "GN", "GW", "HN", "HU", "IS", "ID", "IQ", "IE", "IL", "IT", "JM", "JP",
        "JO", "KZ", "KE", "KI", "KW", "KG", "LA", "LV", "LB", "LS", "LR", "LY", "LT", "LU", "MK", "MG", "MW", "MY", "ML", "MT",
        "MH", "MR", "MU", "MX", "MD", "MN", "ME", "MS", "MA", "MZ", "NA", "NR", "NP", "NZ", "NI", "NE", "NG", "NU", "NF", "NO",
        "OM", "PK", "PW", "PA", "PG", "PY", "PE", "PL", "PT", "QA", "CG", "RO", "RW", "KN", "LC", "VC", "WS", "ST", "SA", "SN",
        "RS", "SC", "SL", "SG", "SK", "SI", "SO", "ZA", "ES", "LK", "SJ", "SZ", "SE", "CH", "TJ", "TZ", "TH", "KM", "FK", "CI",
        "MV", "NL", "PH", "PN", "SB", "TG", "TK", "TO", "TN", "TR", "TM", "TC", "TV", "UG", "UA", "AE", "GB", "US", "UY", "UZ",
        "VU", "VE", "VN", "YE", "ZM", "ZW"
    )

    @Suppress("NewApi")
    fun getDefaultLanguageIndex(settings: Settings?): Int {
        val storedLanguageCode = settings?.getString("languageCode")

        return if (storedLanguageCode != null) {
            languageEntryValues.indexOf(storedLanguageCode).takeIf { it >= 0 } ?: 0
        } else {
            val defaultLanguageCode = Locale.getDefault().toLanguageTag()
            settings?.putString("languageCode", defaultLanguageCode)
            languageEntryValues.indexOf(defaultLanguageCode).takeIf { it >= 0 } ?: 0
        }
    }

    val languageEntryTitles = mutableListOf(
        "English", "French", "German", "Spanish (Spain)", "Italian", "Dutch", "Portuguese", "Russian",
        "Portuguese (Brazil)", "Polish", "Turkish", "Romanian", "Hungarian", "Serbian", "Arabic", "Croatian",
        "Spanish (Mexico)", "Czech", "Slovak", "Swedish", "English (US)", "Japanese", "Bulgarian", "Danish",
        "Finnish", "Slovenian", "Ukrainian"
    )

    val languageEntryValues = mutableListOf(
        "en-US", "fr-FR", "de-DE", "es-ES", "it-IT", "nl-NL", "pt-PT", "ru-RU",
        "pt-BR", "pl-PL", "tr-TR", "ro-RO", "hu-HU", "sr-RS", "ar-AR", "hr-HR",
        "es-MX", "cs-CZ", "sk-SK", "sv-SE", "en-US", "ja-JP", "bg-BG", "da-DK",
        "fi-FI", "sl-SI", "uk-UA"
    )
}

object DeezerUtils {
    var settings: Settings? = null

    private var _arlExpired: Boolean = false
    val arlExpired: Boolean
        get() = _arlExpired

    fun setArlExpired(expired: Boolean) {
        _arlExpired = expired
    }
}

object DeezerCredentialsHolder {
    private var _credentials: DeezerCredentials? = null
    val credentials: DeezerCredentials?
        get() = _credentials

    fun initialize(credentials: DeezerCredentials) {
        if (_credentials == null) {
            _credentials = credentials
        } else {
            throw IllegalStateException("Credentials are already initialized")
        }
    }

    fun updateCredentials(arl: String? = null, sid: String? = null, token: String? = null, userId: String? = null, licenseToken: String? = null, email: String? = null, pass: String? = null) {
        _credentials = _credentials?.copy(
            arl = arl ?: _credentials!!.arl,
            sid = sid ?: _credentials!!.sid,
            token = token ?: _credentials!!.token,
            userId = userId ?: _credentials!!.userId,
            licenseToken = licenseToken ?: _credentials!!.licenseToken,
            email = email ?: _credentials!!.email,
            pass = pass ?: _credentials!!.pass
        ) ?: throw IllegalStateException("Credentials are not initialized")
    }
}

