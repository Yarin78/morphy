package se.yarin.cbhlib;

import lombok.NonNull;

import java.util.HashMap;
import java.util.Map;

// Same numbering scheme is used by ChessBase to enumerate languages, even though only a few are used
public enum Nation {
    NONE("none"),
    AFGHANISTAN("Afghanistan"), // 1
    ALBANIA("Albania"), // 2
    ALGERIA("Algeria"), // 3
    ANDORRA("Andorra"), // 4
    ANGOLA("Angola"), // 5
    ANTIGUA_BARBUDA("Antigua Barbuda"), // 6
    ARGENTINA("Argentina"), // 7
    ARMENIA("Armenia"), // 8
    AUSTRALIA("Australia"), // 9
    AUSTRIA("Austria"), // 10

    AZERBAIJAN("Azerbaijan"), // 11
    BAHAMAS("Bahamas"), // 12
    BAHRAIN("Bahrain"), // 13
    BANGLADESH("Bangladesh"), // 14
    BARBADOS("Barbados"), // 15
    BELARUS("Belarus"), // 16
    BELGIUM("Belgium"), // 17
    BELIZE("Belize"), // 18
    BERMUDA("Bermuda"), // 19
    BOLIVIA("Bolivia"), // 20

    BOSNIA_HERZEGOVINA("Bosnia Herzegovina"), // 21
    BOTSWANA("Botswana"), // 22
    BRAZIL("Brazil"), // 23
    BRITISH_VIRGIN_ISLANDS("British Virgin Islands"), // 24
    BRUNEI("Brunei"), // 25
    BULGARIA("Bulgaria"), // 26
    BURKINA_FASO("Burkina Faso"), // 27
    CANADA("Canada"), // 28
    CHILE("Chile"), // 29
    CHINA("China"), // 30

    COLOMBIA("Colombia"), // 31
    COSTA_RICA("Costa Rica"), // 32
    CROATIA("Croatia"), // 33
    CUBA("Cuba"), // 34
    CYPRUS("Cyprus"), // 35
    CZECH_REPUBLIC("Czech Republic"), // 36
    DENMARK("Denmark"), // 37
    DJIBOUTI("Djibouti"), // 38
    DOMINICAN_REPUBLIC("Dominican Republic"), // 39
    ECUADOR("Ecuador"), // 40

    EGYPT("Egypt"), // 41
    ENGLAND("England"), // 42
    SPAIN("Spain"), // 43
    ESTONIA("Estonia"), // 44
    ETHIOPIA("Ethiopia"), // 45
    FAROE_ISLANDS("Faroe Islands"), // 46
    FIJI("Fiji"), // 47
    FINLAND("Finland"), // 48
    FRANCE("France"), // 49
    MACEDONIA("Macedonia"), // 50

    GAMBIA("Gambia"), // 51
    GEORGIA("Georgia"), // 52
    GERMANY("Germany"), // 53
    GHANA("Ghana"), // 54
    GREECE("Greece"), // 55
    GUATEMALA("Guatemala"), // 56
    CHANNEL_ISLANDS("Channel Islands"), // 57
    GUYANA("Guyana"), // 58
    HAITI("Haiti"), // 59
    HONDURAS("Honduras"), // 60

    HONG_KONG("Hong Kong"), // 61
    HUNGARY("Hungary"), // 62
    ICELAND("Iceland"), // 63
    INDIA("India"), // 64
    INDONESIA("Indonesia"), // 65
    IRAN("Iran"), // 66
    IRAQ("Iraq"), // 67
    ISRAEL("Israel"), // 68
    IRELAND("Ireland"), // 69
    ITALY("Italy"), // 70

    IVORY_COAST("Ivory Coast"), // 71
    JAMAICA("Jamaica"), // 72
    JAPAN("Japan"), // 73
    CHANNEL_ISLANDS_2("Channel Islands (2)"), // 74
    JORDAN("Jordan"), // 75
    KAZAKHSTAN("Kazakhstan"), // 76
    KENYA("Kenya"), // 77
    KOREA("Korea"), // 78
    KYRGYZSTAN("Kyrgyzstan"), // 79
    KUWAIT("Kuwait"), // 80

    LATVIA("Latvia"), // 81
    LEBANON("Lebanon"), // 82
    LIBYA("Libya"), // 83
    LIECHTENSTEIN("Liechtenstein"), // 84
    LITHUANIA("Lithuania"), // 85
    LUXEMBOURG("Luxembourg"), // 86
    MACAU("Macau"), // 87
    MADAGASCAR("Madagascar"), // 88
    MALAYSIA("Malaysia"), // 89
    MALI("Mali"), // 90

    MALTA("Malta"), // 91
    MAURITANIA("Mauritania"), // 92
    MAURITIUS("Mauritius"), // 93
    MEXICO("Mexico"), // 94
    MOLDOVA("Moldova"), // 95
    MONACO("Monaco"), // 96
    MONGOLIA("Mongolia"), // 97
    MOROCCO("Morocco"), // 98
    MOZAMBIQUE("Mozambique"), // 99
    MYANMAR("Myanmar"), // 100

    NAMIBIA("Namibia"), // 101
    NEPAL("Nepal"), // 102
    NETHERLANDS("Netherlands"), // 103
    NETHERLANDS_ANTILLES("Netherlands Antilles"), // 104
    NEW_ZEALAND("New Zealand"), // 105
    NICARAGUA("Nicaragua"), // 106
    NIGERIA("Nigeria"), // 107
    NORWAY("Norway"), // 108
    PAKISTAN("Pakistan"), // 109
    PALESTINE("Palestine"), // 110

    PANAMA("Panama"), // 111
    PAPUA_NEW_GUINEA("Papua New Guinea"), // 112
    PARAGUAY("Paraguay"), // 113
    PERU("Peru"), // 114
    PHILIPPINES("Philippines"), // 115
    POLAND("Poland"), // 116
    PORTUGAL("Portugal"), // 117
    PUERTO_RICO("Puerto Rico"), // 118
    QATAR("Qatar"), // 119
    ROMANIA("Romania"), // 120

    RUSSIA("Russia"), // 121
    EL_SALVADOR("El Salvador"), // 122
    SAN_MARINO("San Marino"), // 123
    SCOTLAND("Scotland"), // 124
    SENEGAL("Senegal"), // 125
    SEYCHELLES("Seychelles"), // 126
    SINGAPORE("Singapore"), // 127
    SLOVAKIA("Slovakia"), // 128
    SLOVENIA("Slovenia"), // 129
    SOUTH_AFRICA("South Africa"), // 130

    SRI_LANKA("Sri Lanka"), // 131
    SUDAN("Sudan"), // 132
    SURINAM("Surinam"), // 133
    SWEDEN("Sweden"), // 134
    SWITZERLAND("Switzerland"), // 135
    SYRIA("Syria"), // 136
    TAJIKISTAN("Tajikistan"), // 137
    TANZANIA("Tanzania"), // 138
    THAILAND("Thailand"), // 139
    TRINIDAD_AND_TOBAGO("Trinidad and Tobago"), // 140

    TUNISIA("Tunisia"), // 141
    TURKEY("Turkey"), // 142
    TURKMENISTAN("Turkmenistan"), // 143
    UGANDA("Uganda"), // 144
    UKRAINE("Ukraine"), // 145
    UNITED_ARAB_EMIRATES("United Arab Emirates"), // 146
    UNITED_STATES("United States"), // 147
    URUGUAY("Uruguay"), // 148
    SOVIET_UNION("Soviet Union"), // 149
    UZBEKISTAN("Uzbekistan"), // 150

    VENEZUELA("Venezuela"), // 151
    VIETNAM("Vietnam"), // 152
    US_VIRGIN_ISLANDS("U.S. Virgin Islands"), // 153
    WALES("Wales"), // 154
    YEMEN("Yemen"), // 155
    SERBIA_AND_MONTENEGRO("Serbia and Montenegro"), // 156
    ZAMBIA("Zambia"), // 157
    ZIMBABWE("Zimbabwe"), // 158
    ZAIRE("Zaire"), // 159
    GERMAN_DEMOCRATIC_REPUBLIC("German Democratic Republic"), // 160

    CZECHOSLOVAKIA("Czechoslovakia"), // 161
    CAMEROON("Cameroon"), // 162
    CHAD("Chad"), // 163
    CAPE_VERDE("Cape Verde"), // 164
    KIRIBATI("Kiribati"), // 165
    COMOROS("Comoros"), // 166
    CONGO("Congo"), // 167
    NORTH_KOREA("North Korea"), // 168
    LAOS("Laos"), // 169
    LESOTHO("Lesotho"), // 170

    MALAWI("Malawi"), // 171
    MALDIVES("Maldives"), // 172
    MARSHALL_ISLANDS("Marshall Islands"), // 173
    OMAN("Oman"), // 174
    NAURU("Nauru"), // 175
    MICRONESIA("Micronesia"), // 176
    NIGER("Niger"), // 177
    SAUDI_ARABIA("Saudi Arabia"), // 178
    TOGO("Togo"), // 179
    TONGA("Tonga"), // 180

    VANUATU("Vanuatu"), // 181
    VATICAN("Vatican"), // 182
    TUVALU("Tuvalu"), // 183
    SWAZILAND("Swaziland"), // 184
    SIERRA_LEONE("Sierra Leone"), // 185
    SANTA_LUCIA("Santa Lucia"), // 186
    PAPUA("Papua"), // 187
    SAN_VINCENT("San Vincent"), // 188
    SAMOA("Samoa"), // 189
    SKITTS("Skitts"), // 190

    SOLOMON_ISLANDS("Solomon Islands"), // 191
    GERMAN_EMPIRE("German Empire"), // 192
    RUSSIAN_EMPIRE("Russian Empire"), // 193
    RWANDA("Rwanda"), // 194
    LIBERIA("Liberia"), // 195
    INTERNET("Internet"), // 196
    TAIWAN("Taiwan"), // 197
    AMERICAN_SAMOA("American Samoa"), // 198
    ANGUILLA("Anguilla"), // 199
    ARUBA("Aruba"), // 200

    BENIN("Benin"), // 201
    BHUTAN("Bhutan"), // 202
    BURUNDI("Burundi"), // 203
    CAMBODIA("Cambodia"), // 204
    CAYMAN_ISLANDS("Cayman Islands"), // 205
    CENTRAL_AFRICAN_REPUBLIC("Central African Republic"), // 206
    CHANNEL_ISLANDS_3("Channel Islands (3)"), // 207
    CHRISTMAS_ISLAND("Christmas Island"), // 208
    COCOS_ISLANDS("Cocos Islands"), // 209
    COOK_ISLANDS("Cook Islands"), // 210

    EQUATORIAL_GUINEA("Equatorial Guinea"), // 211
    ERITREA("Eritrea"), // 212
    FALKLAND_ISLANDS("Falkland Islands"), // 213
    FRENCH_GUYANA("French Guyana"), // 214
    FRENCH_POLYNESIA("French Polynesia"), // 215
    GABON("Gabon"), // 216
    GIBRALTAR("Gibraltar"), // 217
    GRENADA("Grenada"), // 218
    GREENLAND("Greenland"), // 219
    GUADELOUPE("Guadeloupe"), // 220

    GUAM("Guam"), // 221
    GUINEA("Guinea"), // 222
    GUINEA_BISSAU("Guinea-Bissau"), // 223
    ISLE_OF_MAN("Isle of Man"), // 224
    JAN_MAYEN("Jan Mayen"), // 225
    MARTINIQUE("Martinique"), // 226
    MAYOTTE("Mayotte"), // 227
    MONTSERRAT("Montserrat"), // 228
    NEW_CALEDONIA("New Caledonia"), // 229
    NIUE("Niue"), // 230

    NORFOLK_ISLAND("Norfolk Island"), // 231
    NORTH_MARIANA_ISLANDS("North Mariana Islands"), // 232
    EAST_TIMOR("East-Timor"), // 233
    PALAU("Palau"), // 234
    PITCAIRN("Pitcairn"), // 235
    REUNION("Reunion"), // 236
    SAO_TOME_AND_PRINCIPE("Sao Tome and Principe"), // 237
    SOMALIA("Somalia"), // 238
    SVALBARD("Svalbard"), // 239
    ST_HELENA("St. Helena"), // 240

    ST_PIERRE_AND_MIQUELON("St. Pierre and Miquelon"), // 241
    TOKELAU_ISLANDS("Tokelau Islands"), // 242
    TURKS_AND_CAICOS_ISLANDS("Turks- and Caicos Islands"), // 243
    WALLIS_AND_FUTUNA("Wallis and Futuna"), // 244
    NORTHERN_IRELAND("Northern Ireland"), // 245
    INTERNATIONAL_SPACE_STATION("International Space Station"), // 246
    GREAT_BRITAIN("Great Britain"), // 247
    SAARLAND_PROTEKTORAT("Saarland (Protektorat)"), // 248
    MONTENEGRO("Montenegro"), // 249
    SERBIA("Serbia"), // 250

    CATALONIA("Catalonia"), // 251
    BASQUE("Basque"), // 252
    KOSOVO("Kosovo"); // 253

    private String name;

    public String getName() {
        return name;
    }

    Nation(String name) {
        this.name = name;
    }

    static Map<String,Nation> nationByName = new HashMap<>(256);

    static {
        for (Nation nation : Nation.values()) {
            nationByName.put(nation.getName().toLowerCase(), nation);
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
}
