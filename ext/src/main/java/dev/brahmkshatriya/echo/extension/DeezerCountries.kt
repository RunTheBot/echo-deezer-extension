package dev.brahmkshatriya.echo.extension

import dev.brahmkshatriya.echo.common.settings.Settings
import java.util.Locale

object DeezerCountries {

    private const val COUNTRY_CODE_KEY = "countryCode"
    private const val LANGUAGE_CODE_KEY = "languageCode"

    data class Country(val name: String, val code: String)
    data class Language(val name: String, val code: String)

    val countries: List<Country> = listOf(
        Country("Afghanistan", "AF"), Country("Albania", "AL"),
        Country("Algeria", "DZ"), Country("Angola", "AO"),
        Country("Anguilla", "AI"), Country("Antigua and Barbuda", "AG"),
        Country("Argentina", "AR"), Country("Armenia", "AM"),
        Country("Australia", "AU"), Country("Austria", "AT"),
        Country("Azerbaijan", "AZ"), Country("Bahrain", "BH"),
        Country("Bangladesh", "BD"), Country("Barbados", "BB"),
        Country("Belgium", "BE"), Country("Benin", "BJ"),
        Country("Bhutan", "BT"), Country("Bolivia", "BO"),
        Country("Bosnia and Herzegovina", "BA"), Country("Botswana", "BW"),
        Country("Brazil", "BR"), Country("British Indian Ocean Territory", "IO"),
        Country("British Virgin Islands", "VG"), Country("Brunei", "BN"),
        Country("Bulgaria", "BG"), Country("Burkina Faso", "BF"),
        Country("Burundi", "BI"), Country("Cambodia", "KH"),
        Country("Cameroon", "CM"), Country("Canada", "CA"),
        Country("Cape Verde", "CV"), Country("Cayman Islands", "KY"),
        Country("Central African Republic", "CF"), Country("Chad", "TD"),
        Country("Chile", "CL"), Country("Christmas Island", "CX"),
        Country("Cocos Islands", "CC"), Country("Colombia", "CO"),
        Country("Cook Islands", "CK"), Country("Costa Rica", "CR"),
        Country("Croatia", "HR"), Country("Cyprus", "CY"),
        Country("Czech Republic", "CZ"), Country("Democratic Republic of the Congo", "CD"),
        Country("Denmark", "DK"), Country("Djibouti", "DJ"),
        Country("Dominica", "DM"), Country("Dominican Republic", "DO"),
        Country("East Timor", "TL"), Country("Ecuador", "EC"),
        Country("Egypt", "EG"), Country("El Salvador", "SV"),
        Country("Equatorial Guinea", "GQ"), Country("Eritrea", "ER"),
        Country("Estonia", "EE"), Country("Ethiopia", "ET"),
        Country("Federated States of Micronesia", "FM"), Country("Fiji", "FJ"),
        Country("Finland", "FI"), Country("France", "FR"),
        Country("Gabon", "GA"), Country("Gambia", "GM"),
        Country("Georgia", "GE"), Country("Germany", "DE"),
        Country("Ghana", "GH"), Country("Greece", "GR"),
        Country("Grenada", "GD"), Country("Guatemala", "GT"),
        Country("Guinea", "GN"), Country("Guinea-Bissau", "GW"),
        Country("Honduras", "HN"), Country("Hungary", "HU"),
        Country("Iceland", "IS"), Country("Indonesia", "ID"),
        Country("Iraq", "IQ"), Country("Ireland", "IE"),
        Country("Israel", "IL"), Country("Italy", "IT"),
        Country("Jamaica", "JM"), Country("Japan", "JP"),
        Country("Jordan", "JO"), Country("Kazakhstan", "KZ"),
        Country("Kenya", "KE"), Country("Kiribati", "KI"),
        Country("Kuwait", "KW"), Country("Kyrgyzstan", "KG"),
        Country("Laos", "LA"), Country("Latvia", "LV"),
        Country("Lebanon", "LB"), Country("Lesotho", "LS"),
        Country("Liberia", "LR"), Country("Libya", "LY"),
        Country("Lithuania", "LT"), Country("Luxembourg", "LU"),
        Country("North Macedonia", "MK"), Country("Madagascar", "MG"),
        Country("Malawi", "MW"), Country("Malaysia", "MY"),
        Country("Mali", "ML"), Country("Malta", "MT"),
        Country("Marshall Islands", "MH"), Country("Mauritania", "MR"),
        Country("Mauritius", "MU"), Country("Mexico", "MX"),
        Country("Moldova", "MD"), Country("Mongolia", "MN"),
        Country("Montenegro", "ME"), Country("Montserrat", "MS"),
        Country("Morocco", "MA"), Country("Mozambique", "MZ"),
        Country("Namibia", "NA"), Country("Nauru", "NR"),
        Country("Nepal", "NP"), Country("New Zealand", "NZ"),
        Country("Nicaragua", "NI"), Country("Niger", "NE"),
        Country("Nigeria", "NG"), Country("Niue", "NU"),
        Country("Norfolk Island", "NF"), Country("Norway", "NO"),
        Country("Oman", "OM"), Country("Pakistan", "PK"),
        Country("Palau", "PW"), Country("Panama", "PA"),
        Country("Papua New Guinea", "PG"), Country("Paraguay", "PY"),
        Country("Peru", "PE"), Country("Poland", "PL"),
        Country("Portugal", "PT"), Country("Qatar", "QA"),
        Country("Republic of the Congo", "CG"), Country("Romania", "RO"),
        Country("Rwanda", "RW"), Country("Saint Kitts and Nevis", "KN"),
        Country("Saint Lucia", "LC"), Country("Saint Vincent and the Grenadines", "VC"),
        Country("Samoa", "WS"), Country("São Tomé and Príncipe", "ST"),
        Country("Saudi Arabia", "SA"), Country("Senegal", "SN"),
        Country("Serbia", "RS"), Country("Seychelles", "SC"),
        Country("Sierra Leone", "SL"), Country("Singapore", "SG"),
        Country("Slovakia", "SK"), Country("Slovenia", "SI"),
        Country("Somalia", "SO"), Country("South Africa", "ZA"),
        Country("Spain", "ES"), Country("Sri Lanka", "LK"),
        Country("Svalbard and Jan Mayen", "SJ"), Country("Eswatini", "SZ"),
        Country("Sweden", "SE"), Country("Switzerland", "CH"),
        Country("Tajikistan", "TJ"), Country("Tanzania", "TZ"),
        Country("Thailand", "TH"), Country("Comoros", "KM"),
        Country("Falkland Islands", "FK"), Country("Ivory Coast", "CI"),
        Country("Maldives", "MV"), Country("Netherlands", "NL"),
        Country("Philippines", "PH"), Country("Pitcairn Islands", "PN"),
        Country("Solomon Islands", "SB"), Country("Togo", "TG"),
        Country("Tokelau", "TK"), Country("Tonga", "TO"),
        Country("Tunisia", "TN"), Country("Turkey", "TR"),
        Country("Turkmenistan", "TM"), Country("Turks and Caicos Islands", "TC"),
        Country("Tuvalu", "TV"), Country("Uganda", "UG"),
        Country("Ukraine", "UA"), Country("United Arab Emirates", "AE"),
        Country("United Kingdom", "GB"), Country("United States of America", "US"),
        Country("Uruguay", "UY"), Country("Uzbekistan", "UZ"),
        Country("Vanuatu", "VU"), Country("Venezuela", "VE"),
        Country("Vietnam", "VN"), Country("Yemen", "YE"),
        Country("Zambia", "ZM"), Country("Zimbabwe", "ZW")
    )

    val languages: List<Language> = listOf(
        Language("English (UK)", "en-GB"), Language("French", "fr-FR"),
        Language("German", "de-DE"), Language("Spanish (Spain)", "es-ES"),
        Language("Italian", "it-IT"), Language("Dutch", "nl-NL"),
        Language("Portuguese (Portugal)", "pt-PT"), Language("Russian", "ru-RU"),
        Language("Portuguese (Brazil)", "pt-BR"), Language("Polish", "pl-PL"),
        Language("Turkish", "tr-TR"), Language("Romanian", "ro-RO"),
        Language("Hungarian", "hu-HU"), Language("Serbian", "sr-RS"),
        Language("Arabic", "ar-SA"), Language("Croatian", "hr-HR"),
        Language("Spanish (Mexico)", "es-MX"), Language("Czech", "cs-CZ"),
        Language("Slovak", "sk-SK"), Language("Swedish", "sv-SE"),
        Language("English (US)", "en-US"), Language("Japanese", "ja-JP"),
        Language("Bulgarian", "bg-BG"), Language("Danish", "da-DK"),
        Language("Finnish", "fi-FI"), Language("Slovenian", "sl-SI"),
        Language("Ukrainian", "uk-UA")
    )

    fun getDefaultCountryIndex(settings: Settings?): Int {
        val storedCountryCode = settings?.getString(COUNTRY_CODE_KEY)
        val countryCode = storedCountryCode ?: Locale.getDefault().country.also {
            settings?.putString(COUNTRY_CODE_KEY, it)
        }
        return countries.indexOfFirst { it.code.equals(countryCode, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
    }

    fun getDefaultLanguageIndex(settings: Settings?): Int {
        val storedLanguageCode = settings?.getString(LANGUAGE_CODE_KEY)
        val languageCode = storedLanguageCode ?: Locale.getDefault().toLanguageTag().also {
            settings?.putString(LANGUAGE_CODE_KEY, it)
        }
        return languages.indexOfFirst { it.code.equals(languageCode, ignoreCase = true) }.takeIf { it >= 0 } ?: 0
    }
}