package se.yarin.cbhlib;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

// Using IOC codes where possible, filled out with ISO codes
// The same numbering scheme is used by ChessBase to enumerate languages, even though only a few are used
public enum Nation {
    NONE("none", ""),
    AFGHANISTAN("Afghanistan", "AFG"), // 1
    ALBANIA("Albania", "ALB"), // 2
    ALGERIA("Algeria", "ALG"), // 3
    ANDORRA("Andorra", "AND"), // 4
    ANGOLA("Angola", "ANG"), // 5
    ANTIGUA_BARBUDA("Antigua Barbuda", "ANT"), // 6
    ARGENTINA("Argentina", "ARG"), // 7
    ARMENIA("Armenia", "ARM"), // 8
    AUSTRALIA("Australia", "AUS"), // 9
    AUSTRIA("Austria", "AUT"), // 10

    AZERBAIJAN("Azerbaijan", "AZE"), // 11
    BAHAMAS("Bahamas", "BAH"), // 12
    BAHRAIN("Bahrain", "BRN"), // 13
    BANGLADESH("Bangladesh", "BAN"), // 14
    BARBADOS("Barbados", "BAR"), // 15
    BELARUS("Belarus", "BLR"), // 16
    BELGIUM("Belgium", "BEL"), // 17
    BELIZE("Belize", "BIZ"), // 18
    BERMUDA("Bermuda", "BER"), // 19
    BOLIVIA("Bolivia", "BOL"), // 20

    BOSNIA_HERZEGOVINA("Bosnia Herzegovina", "BIH"), // 21
    BOTSWANA("Botswana", "BOT"), // 22
    BRAZIL("Brazil", "BRA"), // 23
    BRITISH_VIRGIN_ISLANDS("British Virgin Islands", "IVB"), // 24
    BRUNEI("Brunei", "BRU"), // 25
    BULGARIA("Bulgaria", "BUL"), // 26
    BURKINA_FASO("Burkina Faso", "BUR"), // 27
    CANADA("Canada", "CAN"), // 28
    CHILE("Chile", "CHI"), // 29
    CHINA("China", "CHN"), // 30

    COLOMBIA("Colombia", "COL"), // 31
    COSTA_RICA("Costa Rica", "CRC"), // 32
    CROATIA("Croatia", "CRO"), // 33
    CUBA("Cuba", "CUB"), // 34
    CYPRUS("Cyprus", "CYP"), // 35
    CZECH_REPUBLIC("Czech Republic", "CZE"), // 36
    DENMARK("Denmark", "DEN"), // 37
    DJIBOUTI("Djibouti", "DJI"), // 38
    DOMINICAN_REPUBLIC("Dominican Republic", "DOM"), // 39
    ECUADOR("Ecuador", "ECU"), // 40

    EGYPT("Egypt", "EGY"), // 41
    ENGLAND("England", "ENG"), // 42
    SPAIN("Spain", "ESP"), // 43
    ESTONIA("Estonia", "EST"), // 44
    ETHIOPIA("Ethiopia", "ETH"), // 45
    FAROE_ISLANDS("Faroe Islands", "FRO"), // 46
    FIJI("Fiji", "FIJ"), // 47
    FINLAND("Finland", "FIN"), // 48
    FRANCE("France", "FRA"), // 49
    MACEDONIA("Macedonia", "MKD"), // 50

    GAMBIA("Gambia", "GAM"), // 51
    GEORGIA("Georgia", "GEO"), // 52
    GERMANY("Germany", "GER"), // 53
    GHANA("Ghana", "GHA"), // 54
    GREECE("Greece", "GRE"), // 55
    GUATEMALA("Guatemala", "GUA"), // 56
    CHANNEL_ISLANDS("Channel Islands", "GCI"), // 57
    GUYANA("Guyana", "GUY"), // 58
    HAITI("Haiti", "HAI"), // 59
    HONDURAS("Honduras", "HON"), // 60

    HONG_KONG("Hong Kong", "HKG"), // 61
    HUNGARY("Hungary", "HUN"), // 62
    ICELAND("Iceland", "ISL"), // 63
    INDIA("India", "IND"), // 64
    INDONESIA("Indonesia", "INA"), // 65
    IRAN("Iran", "IRI"), // 66
    IRAQ("Iraq", "IRQ"), // 67
    ISRAEL("Israel", "ISR"), // 68
    IRELAND("Ireland", "IRL"), // 69
    ITALY("Italy", "ITA"), // 70

    IVORY_COAST("Ivory Coast", "CIV"), // 71
    JAMAICA("Jamaica", "JAM"), // 72
    JAPAN("Japan", "JPN"), // 73
    CHANNEL_ISLANDS_2("Channel Islands (2)", "GCI"), // 74
    JORDAN("Jordan", "JOR"), // 75
    KAZAKHSTAN("Kazakhstan", "KAZ"), // 76
    KENYA("Kenya", "KEN"), // 77
    KOREA("Korea", "KOR"), // 78
    KYRGYZSTAN("Kyrgyzstan", "KGZ"), // 79
    KUWAIT("Kuwait", "KUW"), // 80

    LATVIA("Latvia", "LAT"), // 81
    LEBANON("Lebanon", "LIB"), // 82
    LIBYA("Libya", "LBA"), // 83
    LIECHTENSTEIN("Liechtenstein", "LIE"), // 84
    LITHUANIA("Lithuania", "LTU"), // 85
    LUXEMBOURG("Luxembourg", "LUX"), // 86
    MACAU("Macau", "MAC"), // 87
    MADAGASCAR("Madagascar", "MAD"), // 88
    MALAYSIA("Malaysia", "MAS"), // 89
    MALI("Mali", "MLI"), // 90

    MALTA("Malta", "MLT"), // 91
    MAURITANIA("Mauritania", "MTN"), // 92
    MAURITIUS("Mauritius", "MRI"), // 93
    MEXICO("Mexico", "MEX"), // 94
    MOLDOVA("Moldova", "MDA"), // 95
    MONACO("Monaco", "MON"), // 96
    MONGOLIA("Mongolia", "MGL"), // 97
    MOROCCO("Morocco", "MAR"), // 98
    MOZAMBIQUE("Mozambique", "MOZ"), // 99
    MYANMAR("Myanmar", "MYA"), // 100

    NAMIBIA("Namibia", "NAM"), // 101
    NEPAL("Nepal", "NEP"), // 102
    NETHERLANDS("Netherlands", "NED"), // 103
    NETHERLANDS_ANTILLES("Netherlands Antilles", "AHO"), // 104
    NEW_ZEALAND("New Zealand", "NZL"), // 105
    NICARAGUA("Nicaragua", "NCA"), // 106
    NIGERIA("Nigeria", "NGR"), // 107
    NORWAY("Norway", "NOR"), // 108
    PAKISTAN("Pakistan", "PAK"), // 109
    PALESTINE("Palestine", "PLE"), // 110

    PANAMA("Panama", "PAN"), // 111
    PAPUA_NEW_GUINEA("Papua New Guinea", "PNG"), // 112
    PARAGUAY("Paraguay", "PAR"), // 113
    PERU("Peru", "PER"), // 114
    PHILIPPINES("Philippines", "PHI"), // 115
    POLAND("Poland", "POL"), // 116
    PORTUGAL("Portugal", "POR"), // 117
    PUERTO_RICO("Puerto Rico", "PUR"), // 118
    QATAR("Qatar", "QAT"), // 119
    ROMANIA("Romania", "ROU"), // 120

    RUSSIA("Russia", "RUS"), // 121
    EL_SALVADOR("El Salvador", "ESA"), // 122
    SAN_MARINO("San Marino", "SMR"), // 123
    SCOTLAND("Scotland", "SCO"), // 124
    SENEGAL("Senegal", "SEN"), // 125
    SEYCHELLES("Seychelles", "SEY"), // 126
    SINGAPORE("Singapore", "SIN"), // 127
    SLOVAKIA("Slovakia", "SVK"), // 128
    SLOVENIA("Slovenia", "SLO"), // 129
    SOUTH_AFRICA("South Africa", "RSA"), // 130

    SRI_LANKA("Sri Lanka", "SRI"), // 131
    SUDAN("Sudan", "SUD"), // 132
    SURINAM("Surinam", "SUR"), // 133
    SWEDEN("Sweden", "SWE"), // 134
    SWITZERLAND("Switzerland", "SUI"), // 135
    SYRIA("Syria", "SYR"), // 136
    TAJIKISTAN("Tajikistan", "TJK"), // 137
    TANZANIA("Tanzania", "TAN"), // 138
    THAILAND("Thailand", "THA"), // 139
    TRINIDAD_AND_TOBAGO("Trinidad and Tobago", "TTO"), // 140

    TUNISIA("Tunisia", "TUN"), // 141
    TURKEY("Turkey", "TUR"), // 142
    TURKMENISTAN("Turkmenistan", "TKM"), // 143
    UGANDA("Uganda", "UGA"), // 144
    UKRAINE("Ukraine", "UKR"), // 145
    UNITED_ARAB_EMIRATES("United Arab Emirates", "UAE"), // 146
    UNITED_STATES("United States", "USA"), // 147
    URUGUAY("Uruguay", "URU"), // 148
    SOVIET_UNION("Soviet Union", "URS"), // 149
    UZBEKISTAN("Uzbekistan", "UZB"), // 150

    VENEZUELA("Venezuela", "VEN"), // 151
    VIETNAM("Vietnam", "VIE"), // 152
    US_VIRGIN_ISLANDS("U.S. Virgin Islands", "ISV"), // 153
    WALES("Wales", "WLS"), // 154
    YEMEN("Yemen", "YEM"), // 155
    SERBIA_AND_MONTENEGRO("Serbia and Montenegro", "SCG"), // 156
    ZAMBIA("Zambia", "ZAM"), // 157
    ZIMBABWE("Zimbabwe", "ZIM"), // 158
    ZAIRE("Zaire", "ZAI"), // 159
    GERMAN_DEMOCRATIC_REPUBLIC("German Democratic Republic", "DDR"), // 160

    CZECHOSLOVAKIA("Czechoslovakia", "TCH"), // 161
    CAMEROON("Cameroon", "CMR"), // 162
    CHAD("Chad", "CHA"), // 163
    CAPE_VERDE("Cape Verde", "CPV"), // 164
    KIRIBATI("Kiribati", "KIR"), // 165
    COMOROS("Comoros", "COM"), // 166
    CONGO("Congo", "CGO"), // 167
    NORTH_KOREA("North Korea", "PRK"), // 168
    LAOS("Laos", "LAO"), // 169
    LESOTHO("Lesotho", "LES"), // 170

    MALAWI("Malawi", "MAW"), // 171
    MALDIVES("Maldives", "MDV"), // 172
    MARSHALL_ISLANDS("Marshall Islands", "MHL"), // 173
    OMAN("Oman", "OMA"), // 174
    NAURU("Nauru", "NRU"), // 175
    MICRONESIA("Micronesia", "MIC"), // 176
    NIGER("Niger", "NIG"), // 177
    SAUDI_ARABIA("Saudi Arabia", "KSA"), // 178
    TOGO("Togo", "TOG"), // 179
    TONGA("Tonga", "TGA"), // 180

    VANUATU("Vanuatu" ,"VAN"), // 181
    VATICAN("Vatican", "VAT"), // 182
    TUVALU("Tuvalu", "TUV"), // 183
    SWAZILAND("Swaziland", "SWZ"), // 184
    SIERRA_LEONE("Sierra Leone", "SLE"), // 185
    SANTA_LUCIA("Santa Lucia", "LCA"), // 186
    PAPUA("Papua", "PAP"), // 187
    SAN_VINCENT("San Vincent", "SVI"), // 188
    SAMOA("Samoa", "SAM"), // 189
    SKITTS("Skitts", "SKI"), // 190

    SOLOMON_ISLANDS("Solomon Islands", "SOL"), // 191
    GERMAN_EMPIRE("German Empire", "GE2"), // 192
    RUSSIAN_EMPIRE("Russian Empire", "ZAR"), // 193
    RWANDA("Rwanda", "RWA"), // 194
    LIBERIA("Liberia", "LBR"), // 195
    INTERNET("Internet", "NET"), // 196
    TAIWAN("Taiwan", "TPE"), // 197
    AMERICAN_SAMOA("American Samoa", "ASA"), // 198
    ANGUILLA("Anguilla", "AGG"), // 199
    ARUBA("Aruba", "ARU"), // 200

    BENIN("Benin", "BEN"), // 201
    BHUTAN("Bhutan", "BHU"), // 202
    BURUNDI("Burundi", "BDI"), // 203
    CAMBODIA("Cambodia", "CAM"), // 204
    CAYMAN_ISLANDS("Cayman Islands", "CAY"), // 205
    CENTRAL_AFRICAN_REPUBLIC("Central African Republic", "CAF"), // 206
    CHANNEL_ISLANDS_3("Channel Islands (3)", "GCI"), // 207
    CHRISTMAS_ISLAND("Christmas Island", "CIA"), // 208
    COCOS_ISLANDS("Cocos Islands", "COA"), // 209
    COOK_ISLANDS("Cook Islands", "COK"), // 210

    EQUATORIAL_GUINEA("Equatorial Guinea", "GEQ"), // 211
    ERITREA("Eritrea", "ERI"), // 212
    FALKLAND_ISLANDS("Falkland Islands", "FGB"), // 213
    FRENCH_GUYANA("French Guyana", "FRG"), // 214
    FRENCH_POLYNESIA("French Polynesia", "FRP"), // 215
    GABON("Gabon", "GAB"), // 216
    GIBRALTAR("Gibraltar", "GGB"), // 217
    GRENADA("Grenada", "GRN"), // 218
    GREENLAND("Greenland", "GRL"), // 219
    GUADELOUPE("Guadeloupe", "FGA"), // 220

    GUAM("Guam", "GUM"), // 221
    GUINEA("Guinea", "GUI"), // 222
    GUINEA_BISSAU("Guinea-Bissau", "GBS"), // 223
    ISLE_OF_MAN("Isle of Man", "IOM"), // 224
    JAN_MAYEN("Jan Mayen", "JMY"), // 225
    MARTINIQUE("Martinique", "MFR"), // 226
    MAYOTTE("Mayotte", "MYF"), // 227
    MONTSERRAT("Montserrat", "MSG"), // 228
    NEW_CALEDONIA("New Caledonia", "NCF"), // 229
    NIUE("Niue", "NNN"), // 230

    NORFOLK_ISLAND("Norfolk Island", "NNA"), // 231
    NORTH_MARIANA_ISLANDS("North Mariana Islands", "NMI"), // 232
    EAST_TIMOR("East-Timor", "OTM"), // 233
    PALAU("Palau", "PLW"), // 234
    PITCAIRN("Pitcairn", "PIG"), // 235
    REUNION("Reunion", "RUF"), // 236
    SAO_TOME_AND_PRINCIPE("Sao Tome and Principe", "STP"), // 237
    SOMALIA("Somalia", "SOM"), // 238
    SVALBARD("Svalbard", "SVN"), // 239
    ST_HELENA("St. Helena", "HGB"), // 240

    ST_PIERRE_AND_MIQUELON("St. Pierre and Miquelon", "PGB"), // 241
    TOKELAU_ISLANDS("Tokelau Islands", "TKI"), // 242
    TURKS_AND_CAICOS_ISLANDS("Turks- and Caicos Islands", "TCI"), // 243
    WALLIS_AND_FUTUNA("Wallis and Futuna", "WFR"), // 244
    NORTHERN_IRELAND("Northern Ireland", "NIR"), // 245
    INTERNATIONAL_SPACE_STATION("International Space Station", "ISS"), // 246
    GREAT_BRITAIN("Great Britain", "GBR"), // 247
    SAARLAND_PROTEKTORAT("Saarland (Protektorat)", "SAA"), // 248
    MONTENEGRO("Montenegro", "MNE"), // 249
    SERBIA("Serbia", "SRB"), // 250

    CATALONIA("Catalonia", "CAT"), // 251
    BASQUE("Basque", "BAS"), // 252
    KOSOVO("Kosovo", "KOS"); // 253

    private String name;
    private String iocCode;

    public String getName() {
        return name;
    }

    public String getIocCode() {
        return iocCode;
    }

    Nation(String name, String iocCode) {
        this.name = name;
        this.iocCode = iocCode;
    }

    static Map<String,Nation> nationByName = new HashMap<>(256);
    static Map<String,Nation> nationByIoc = new HashMap<>(256);

    static {
        for (Nation nation : Nation.values()) {
            nationByName.put(nation.getName().toLowerCase(), nation);
            nationByIoc.put(nation.getIocCode().toUpperCase(), nation);
        }
    }

    /**
     * Gets the Nation that matches the specified name
     * @param name the name of the nation, case insensitive
     * @return a Nation, or {@link Nation#NONE} if there is no match
     */
    public static Nation fromName(@NonNull String name) {
        Nation nation = nationByName.get(name.toLowerCase());
        if (nation == null) {
            nation = Nation.NONE;
        }
        return nation;
    }

    /**
     * Gets the Nation that matches the specified IOC code
     * @param ioc the IOC country code, case insensitive
     * @return a Nation, or {@link Nation#NONE} if there is no match
     */
    public static Nation fromIOC(@NonNull String ioc) {
        Nation nation = nationByIoc.get(ioc.toUpperCase());
        if (nation == null) {
            nation = Nation.NONE;
        }
        return nation;
    }
}
