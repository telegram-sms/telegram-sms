package com.github.sumimakito.codeauxlib

import android.content.Context
import org.json.JSONObject
import java.util.*

class CodeauxLibPortable {
    private val preProcessOmitRegexExpressions = ArrayList<Regex>()
    private val preProcessReplaceRegexExpressions = ArrayList<Regex>()
    private val recipes = ArrayList<RegexRecipe>()

    init {
        try {
            val configJson = JSONObject(CONFIG_JSON)
            val preProcessJson = configJson.getJSONObject("pre_process")
            val omit = preProcessJson.getJSONArray("omit")
            for (i in 0 until omit.length()) {
                preProcessOmitRegexExpressions.add(Regex(omit.getString(i), setOf(RegexOption.IGNORE_CASE)))
            }
            val replace = preProcessJson.getJSONArray("replace")
            for (i in 0 until replace.length()) {
                preProcessReplaceRegexExpressions.add(Regex(replace.getString(i), setOf(RegexOption.IGNORE_CASE)))
            }
            recipes.add(RegexRecipe.fromJson(JSONObject(L_ZH_JSON)))
            recipes.add(RegexRecipe.fromJson(JSONObject(L_JA_JSON)))
            recipes.add(RegexRecipe.fromJson(JSONObject(L_EN_JSON)))
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw RuntimeException("Failed to initialize CodeauxLib")
        }
    }

    fun find(input: String): String? {
        var inputString = input
        preProcessOmitRegexExpressions.forEach { r ->
            inputString = inputString.replace(r, "")
        }
        preProcessReplaceRegexExpressions.forEach { r ->
            inputString = inputString.replace(r, " ")
        }
        recipes.forEach { recipe ->
            val result = RegexPipeline.with(inputString, recipe)
            if (result != null) {
                return result
            }
        }
        return null
    }

    companion object {
        private const val CONFIG_JSON =
                "{\"locale_priorities\":[\"zh\",\"ja\",\"en\"],\"pre_process\":{\"omit\":[\"(https?|ftp|file):\\\\/\\\\/[-a-zA-Z0-9+&@#\\\\/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#\\\\/%=~_|]\",\"[a-zA-Z0-9]+(\\\\.[a-zA-Z0-9]+)?\\\\.(?:aaa|aarp|abarth|abb|abbott|abbvie|abc|able|abogado|abudhabi|ac|academy|accenture|accountant|accountants|aco|actor|ad|adac|ads|adult|ae|aeg|aero|aetna|af|afamilycompany|afl|africa|ag|agakhan|agency|ai|aig|aigo|airbus|airforce|airtel|akdn|al|alfaromeo|alibaba|alipay|allfinanz|allstate|ally|alsace|alstom|am|americanexpress|americanfamily|amex|amfam|amica|amsterdam|analytics|android|anquan|anz|ao|aol|apartments|app|apple|aq|aquarelle|ar|arab|aramco|archi|army|arpa|art|arte|as|asda|asia|associates|at|athleta|attorney|au|auction|audi|audible|audio|auspost|author|auto|autos|avianca|aw|aws|ax|axa|az|azure|ba|baby|baidu|banamex|bananarepublic|band|bank|bar|barcelona|barclaycard|barclays|barefoot|bargains|baseball|basketball|bauhaus|bayern|bb|bbc|bbt|bbva|bcg|bcn|bd|be|beats|beauty|beer|bentley|berlin|best|bestbuy|bet|bf|bg|bh|bharti|bi|bible|bid|bike|bing|bingo|bio|biz|bj|black|blackfriday|blockbuster|blog|bloomberg|blue|bm|bms|bmw|bn|bnl|bnpparibas|bo|boats|boehringer|bofa|bom|bond|boo|book|booking|bosch|bostik|boston|bot|boutique|box|br|bradesco|bridgestone|broadway|broker|brother|brussels|bs|bt|budapest|bugatti|build|builders|business|buy|buzz|bv|bw|by|bz|bzh|ca|cab|cafe|cal|call|calvinklein|cam|camera|camp|cancerresearch|canon|capetown|capital|capitalone|car|caravan|cards|care|career|careers|cars|cartier|casa|case|caseih|cash|casino|cat|catering|catholic|cba|cbn|cbre|cbs|cc|cd|ceb|center|ceo|cern|cf|cfa|cfd|cg|ch|chanel|channel|charity|chase|chat|cheap|chintai|christmas|chrome|chrysler|church|ci|cipriani|circle|cisco|citadel|citi|citic|city|cityeats|ck|cl|claims|cleaning|click|clinic|clinique|clothing|cloud|club|clubmed|cm|cn|co|coach|codes|coffee|college|cologne|com|comcast|commbank|community|company|compare|computer|comsec|condos|construction|consulting|contact|contractors|cooking|cookingchannel|cool|coop|corsica|country|coupon|coupons|courses|cr|credit|creditcard|creditunion|cricket|crown|crs|cruise|cruises|csc|cu|cuisinella|cv|cw|cx|cy|cymru|cyou|cz|dabur|dad|dance|data|date|dating|datsun|day|dclk|dds|de|deal|dealer|deals|degree|delivery|dell|deloitte|delta|democrat|dental|dentist|desi|design|dev|dhl|diamonds|diet|digital|direct|directory|discount|discover|dish|diy|dj|dk|dm|dnp|do|docs|doctor|dodge|dog|domains|dot|download|drive|dtv|dubai|duck|dunlop|duns|dupont|durban|dvag|dvr|dz|earth|eat|ec|eco|edeka|edu|education|ee|eg|email|emerck|energy|engineer|engineering|enterprises|epson|equipment|er|ericsson|erni|es|esq|estate|esurance|et|etisalat|eu|eurovision|eus|events|everbank|exchange|expert|exposed|express|extraspace|fage|fail|fairwinds|faith|family|fan|fans|farm|farmers|fashion|fast|fedex|feedback|ferrari|ferrero|fi|fiat|fidelity|fido|film|final|finance|financial|fire|firestone|firmdale|fish|fishing|fit|fitness|fj|fk|flickr|flights|flir|florist|flowers|fly|fm|fo|foo|food|foodnetwork|football|ford|forex|forsale|forum|foundation|fox|fr|free|fresenius|frl|frogans|frontdoor|frontier|ftr|fujitsu|fujixerox|fun|fund|furniture|futbol|fyi|ga|gal|gallery|gallo|gallup|game|games|gap|garden|gb|gbiz|gd|gdn|ge|gea|gent|genting|george|gf|gg|ggee|gh|gi|gift|gifts|gives|giving|gl|glade|glass|gle|global|globo|gm|gmail|gmbh|gmo|gmx|gn|godaddy|gold|goldpoint|golf|goo|goodyear|goog|google|gop|got|gov|gp|gq|gr|grainger|graphics|gratis|green|gripe|grocery|group|gs|gt|gu|guardian|gucci|guge|guide|guitars|guru|gw|gy|hair|hamburg|hangout|haus|hbo|hdfc|hdfcbank|health|healthcare|help|helsinki|here|hermes|hgtv|hiphop|hisamitsu|hitachi|hiv|hk|hkt|hm|hn|hockey|holdings|holiday|homedepot|homegoods|homes|homesense|honda|horse|hospital|host|hosting|hot|hoteles|hotels|hotmail|house|how|hr|hsbc|ht|hu|hughes|hyatt|hyundai|ibm|icbc|ice|icu|id|ie|ieee|ifm|ikano|il|im|imamat|imdb|immo|immobilien|in|inc|industries|infiniti|info|ing|ink|institute|insurance|insure|int|intel|international|intuit|investments|io|ipiranga|iq|ir|irish|is|iselect|ismaili|ist|istanbul|it|itau|itv|iveco|jaguar|java|jcb|jcp|je|jeep|jetzt|jewelry|jio|jll|jm|jmp|jnj|jo|jobs|joburg|jot|joy|jp|jpmorgan|jprs|juegos|juniper|kaufen|kddi|ke|kerryhotels|kerrylogistics|kerryproperties|kfh|kg|kh|ki|kia|kim|kinder|kindle|kitchen|kiwi|km|kn|koeln|komatsu|kosher|kp|kpmg|kpn|kr|krd|kred|kuokgroup|kw|ky|kyoto|kz|la|lacaixa|ladbrokes|lamborghini|lamer|lancaster|lancia|lancome|land|landrover|lanxess|lasalle|lat|latino|latrobe|law|lawyer|lb|lc|lds|lease|leclerc|lefrak|legal|lego|lexus|lgbt|li|liaison|lidl|life|lifeinsurance|lifestyle|lighting|like|lilly|limited|limo|lincoln|linde|link|lipsy|live|living|lixil|lk|llc|loan|loans|locker|locus|loft|lol|london|lotte|lotto|love|lpl|lplfinancial|lr|ls|lt|ltd|ltda|lu|lundbeck|lupin|luxe|luxury|lv|ly|ma|macys|madrid|maif|maison|makeup|man|management|mango|map|market|marketing|markets|marriott|marshalls|maserati|mattel|mba|mc|mckinsey|md|me|med|media|meet|melbourne|meme|memorial|men|menu|merckmsd|metlife|mg|mh|miami|microsoft|mil|mini|mint|mit|mitsubishi|mk|ml|mlb|mls|mm|mma|mn|mo|mobi|mobile|mobily|moda|moe|moi|mom|monash|money|monster|mopar|mormon|mortgage|moscow|moto|motorcycles|mov|movie|movistar|mp|mq|mr|ms|msd|mt|mtn|mtr|mu|museum|mutual|mv|mw|mx|my|mz|na|nab|nadex|nagoya|name|nationwide|natura|navy|nba|nc|ne|nec|net|netbank|netflix|network|neustar|new|newholland|news|next|nextdirect|nexus|nf|nfl|ng|ngo|nhk|ni|nico|nike|nikon|ninja|nissan|nissay|nl|no|nokia|northwesternmutual|norton|now|nowruz|nowtv|np|nr|nra|nrw|ntt|nu|nyc|nz|obi|observer|off|office|okinawa|olayan|olayangroup|oldnavy|ollo|om|omega|one|ong|onl|online|onyourside|ooo|open|oracle|orange|org|organic|origins|osaka|otsuka|ott|ovh|pa|page|panasonic|paris|pars|partners|parts|party|passagens|pay|pccw|pe|pet|pf|pfizer|pg|ph|pharmacy|phd|philips|phone|photo|photography|photos|physio|piaget|pics|pictet|pictures|pid|pin|ping|pink|pioneer|pizza|pk|pl|place|play|playstation|plumbing|plus|pm|pn|pnc|pohl|poker|politie|porn|post|pr|pramerica|praxi|press|prime|pro|prod|productions|prof|progressive|promo|properties|property|protection|pru|prudential|ps|pt|pub|pw|pwc|py|qa|qpon|quebec|quest|qvc|racing|radio|raid|re|read|realestate|realtor|realty|recipes|red|redstone|redumbrella|rehab|reise|reisen|reit|reliance|ren|rent|rentals|repair|report|republican|rest|restaurant|review|reviews|rexroth|rich|richardli|ricoh|rightathome|ril|rio|rip|rmit|ro|rocher|rocks|rodeo|rogers|room|rs|rsvp|ru|rugby|ruhr|run|rw|rwe|ryukyu|sa|saarland|safe|safety|sakura|sale|salon|samsclub|samsung|sandvik|sandvikcoromant|sanofi|sap|sarl|sas|save|saxo|sb|sbi|sbs|sc|sca|scb|schaeffler|schmidt|scholarships|school|schule|schwarz|science|scjohnson|scor|scot|sd|se|search|seat|secure|security|seek|select|sener|services|ses|seven|sew|sex|sexy|sfr|sg|sh|shangrila|sharp|shaw|shell|shia|shiksha|shoes|shop|shopping|shouji|show|showtime|shriram|si|silk|sina|singles|site|sj|sk|ski|skin|sky|skype|sl|sling|sm|smart|smile|sn|sncf|so|soccer|social|softbank|software|sohu|solar|solutions|song|sony|soy|space|sport|spot|spreadbetting|sr|srl|srt|ss|st|stada|staples|star|starhub|statebank|statefarm|stc|stcgroup|stockholm|storage|store|stream|studio|study|style|su|sucks|supplies|supply|support|surf|surgery|suzuki|sv|swatch|swiftcover|swiss|sx|sy|sydney|symantec|systems|sz|tab|taipei|talk|taobao|target|tatamotors|tatar|tattoo|tax|taxi|tc|tci|td|tdk|team|tech|technology|tel|telefonica|temasek|tennis|teva|tf|tg|th|thd|theater|theatre|tiaa|tickets|tienda|tiffany|tips|tires|tirol|tj|tjmaxx|tjx|tk|tkmaxx|tl|tm|tmall|tn|to|today|tokyo|tools|top|toray|toshiba|total|tours|town|toyota|toys|tr|trade|trading|training|travel|travelchannel|travelers|travelersinsurance|trust|trv|tt|tube|tui|tunes|tushu|tv|tvs|tw|tz|ua|ubank|ubs|uconnect|ug|uk|unicom|university|uno|uol|ups|us|uy|uz|va|vacations|vana|vanguard|vc|ve|vegas|ventures|verisign|versicherung|vet|vg|vi|viajes|video|vig|viking|villas|vin|vip|virgin|visa|vision|vistaprint|viva|vivo|vlaanderen|vn|vodka|volkswagen|volvo|vote|voting|voto|voyage|vu|vuelos|wales|walmart|walter|wang|wanggou|warman|watch|watches|weather|weatherchannel|webcam|weber|website|wed|wedding|weibo|weir|wf|whoswho|wien|wiki|williamhill|win|windows|wine|winners|wme|wolterskluwer|woodside|work|works|world|wow|ws|wtc|wtf|xbox|xerox|xfinity|xihuan|xin|xn--11b4c3d|xn--1ck2e1b|xn--1qqw23a|xn--2scrj9c|xn--30rr7y|xn--3bst00m|xn--3ds443g|xn--3e0b707e|xn--3hcrj9c|xn--3oq18vl8pn36a|xn--3pxu8k|xn--42c2d9a|xn--45br5cyl|xn--45brj9c|xn--45q11c|xn--4gbrim|xn--54b7fta0cc|xn--55qw42g|xn--55qx5d|xn--5su34j936bgsg|xn--5tzm5g|xn--6frz82g|xn--6qq986b3xl|xn--80adxhks|xn--80ao21a|xn--80aqecdr1a|xn--80asehdb|xn--80aswg|xn--8y0a063a|xn--90a3ac|xn--90ae|xn--90ais|xn--9dbq2a|xn--9et52u|xn--9krt00a|xn--b4w605ferd|xn--bck1b9a5dre4c|xn--c1avg|xn--c2br7g|xn--cck2b3b|xn--cg4bki|xn--clchc0ea0b2g2a9gcd|xn--czr694b|xn--czrs0t|xn--czru2d|xn--d1acj3b|xn--d1alf|xn--e1a4c|xn--eckvdtc9d|xn--efvy88h|xn--estv75g|xn--fct429k|xn--fhbei|xn--fiq228c5hs|xn--fiq64b|xn--fiqs8s|xn--fiqz9s|xn--fjq720a|xn--flw351e|xn--fpcrj9c3d|xn--fzc2c9e2c|xn--fzys8d69uvgm|xn--g2xx48c|xn--gckr3f0f|xn--gecrj9c|xn--gk3at1e|xn--h2breg3eve|xn--h2brj9c|xn--h2brj9c8c|xn--hxt814e|xn--i1b6b1a6a2e|xn--imr513n|xn--io0a7i|xn--j1aef|xn--j1amh|xn--j6w193g|xn--jlq61u9w7b|xn--jvr189m|xn--kcrx77d1x4a|xn--kprw13d|xn--kpry57d|xn--kpu716f|xn--kput3i|xn--l1acc|xn--lgbbat1ad8j|xn--mgb9awbf|xn--mgba3a3ejt|xn--mgba3a4f16a|xn--mgba7c0bbn0a|xn--mgbaakc7dvf|xn--mgbaam7a8h|xn--mgbab2bd|xn--mgbah1a3hjkrd|xn--mgbai9azgqp6j|xn--mgbayh7gpa|xn--mgbb9fbpob|xn--mgbbh1a|xn--mgbbh1a71e|xn--mgbc0a9azcg|xn--mgbca7dzdo|xn--mgberp4a5d4ar|xn--mgbgu82a|xn--mgbi4ecexp|xn--mgbpl2fh|xn--mgbt3dhd|xn--mgbtx2b|xn--mgbx4cd0ab|xn--mix891f|xn--mk1bu44c|xn--mxtq1m|xn--ngbc5azd|xn--ngbe9e0a|xn--ngbrx|xn--node|xn--nqv7f|xn--nqv7fs00ema|xn--nyqy26a|xn--o3cw4h|xn--ogbpf8fl|xn--otu796d|xn--p1acf|xn--p1ai|xn--pbt977c|xn--pgbs0dh|xn--pssy2u|xn--q9jyb4c|xn--qcka1pmc|xn--qxam|xn--rhqv96g|xn--rovu88b|xn--rvc1e0am3e|xn--s9brj9c|xn--ses554g|xn--t60b56a|xn--tckwe|xn--tiq49xqyj|xn--unup4y|xn--vermgensberater-ctb|xn--vermgensberatung-pwb|xn--vhquv|xn--vuq861b|xn--w4r85el8fhu5dnra|xn--w4rs40l|xn--wgbh1c|xn--wgbl6a|xn--xhq521b|xn--xkc2al3hye2a|xn--xkc2dl3a5ee0h|xn--y9a3aq|xn--yfro4i67o|xn--ygbi2ammx|xn--zfr164b|xxx|xyz|yachts|yahoo|yamaxun|yandex|ye|yodobashi|yoga|yokohama|you|youtube|yt|yun|za|zappos|zara|zero|zip|zm|zone|zuerich|zw)/[-a-zA-Z0-9+&@#\\\\/%=~_|.]*\",\"[a-zA-Z0-9]+(\\\\.[a-zA-Z0-9]+)?\\\\.(?:aaa|aarp|abarth|abb|abbott|abbvie|abc|able|abogado|abudhabi|ac|academy|accenture|accountant|accountants|aco|actor|ad|adac|ads|adult|ae|aeg|aero|aetna|af|afamilycompany|afl|africa|ag|agakhan|agency|ai|aig|aigo|airbus|airforce|airtel|akdn|al|alfaromeo|alibaba|alipay|allfinanz|allstate|ally|alsace|alstom|am|americanexpress|americanfamily|amex|amfam|amica|amsterdam|analytics|android|anquan|anz|ao|aol|apartments|app|apple|aq|aquarelle|ar|arab|aramco|archi|army|arpa|art|arte|as|asda|asia|associates|at|athleta|attorney|au|auction|audi|audible|audio|auspost|author|auto|autos|avianca|aw|aws|ax|axa|az|azure|ba|baby|baidu|banamex|bananarepublic|band|bank|bar|barcelona|barclaycard|barclays|barefoot|bargains|baseball|basketball|bauhaus|bayern|bb|bbc|bbt|bbva|bcg|bcn|bd|be|beats|beauty|beer|bentley|berlin|best|bestbuy|bet|bf|bg|bh|bharti|bi|bible|bid|bike|bing|bingo|bio|biz|bj|black|blackfriday|blockbuster|blog|bloomberg|blue|bm|bms|bmw|bn|bnl|bnpparibas|bo|boats|boehringer|bofa|bom|bond|boo|book|booking|bosch|bostik|boston|bot|boutique|box|br|bradesco|bridgestone|broadway|broker|brother|brussels|bs|bt|budapest|bugatti|build|builders|business|buy|buzz|bv|bw|by|bz|bzh|ca|cab|cafe|cal|call|calvinklein|cam|camera|camp|cancerresearch|canon|capetown|capital|capitalone|car|caravan|cards|care|career|careers|cars|cartier|casa|case|caseih|cash|casino|cat|catering|catholic|cba|cbn|cbre|cbs|cc|cd|ceb|center|ceo|cern|cf|cfa|cfd|cg|ch|chanel|channel|charity|chase|chat|cheap|chintai|christmas|chrome|chrysler|church|ci|cipriani|circle|cisco|citadel|citi|citic|city|cityeats|ck|cl|claims|cleaning|click|clinic|clinique|clothing|cloud|club|clubmed|cm|cn|co|coach|codes|coffee|college|cologne|com|comcast|commbank|community|company|compare|computer|comsec|condos|construction|consulting|contact|contractors|cooking|cookingchannel|cool|coop|corsica|country|coupon|coupons|courses|cr|credit|creditcard|creditunion|cricket|crown|crs|cruise|cruises|csc|cu|cuisinella|cv|cw|cx|cy|cymru|cyou|cz|dabur|dad|dance|data|date|dating|datsun|day|dclk|dds|de|deal|dealer|deals|degree|delivery|dell|deloitte|delta|democrat|dental|dentist|desi|design|dev|dhl|diamonds|diet|digital|direct|directory|discount|discover|dish|diy|dj|dk|dm|dnp|do|docs|doctor|dodge|dog|domains|dot|download|drive|dtv|dubai|duck|dunlop|duns|dupont|durban|dvag|dvr|dz|earth|eat|ec|eco|edeka|edu|education|ee|eg|email|emerck|energy|engineer|engineering|enterprises|epson|equipment|er|ericsson|erni|es|esq|estate|esurance|et|etisalat|eu|eurovision|eus|events|everbank|exchange|expert|exposed|express|extraspace|fage|fail|fairwinds|faith|family|fan|fans|farm|farmers|fashion|fast|fedex|feedback|ferrari|ferrero|fi|fiat|fidelity|fido|film|final|finance|financial|fire|firestone|firmdale|fish|fishing|fit|fitness|fj|fk|flickr|flights|flir|florist|flowers|fly|fm|fo|foo|food|foodnetwork|football|ford|forex|forsale|forum|foundation|fox|fr|free|fresenius|frl|frogans|frontdoor|frontier|ftr|fujitsu|fujixerox|fun|fund|furniture|futbol|fyi|ga|gal|gallery|gallo|gallup|game|games|gap|garden|gb|gbiz|gd|gdn|ge|gea|gent|genting|george|gf|gg|ggee|gh|gi|gift|gifts|gives|giving|gl|glade|glass|gle|global|globo|gm|gmail|gmbh|gmo|gmx|gn|godaddy|gold|goldpoint|golf|goo|goodyear|goog|google|gop|got|gov|gp|gq|gr|grainger|graphics|gratis|green|gripe|grocery|group|gs|gt|gu|guardian|gucci|guge|guide|guitars|guru|gw|gy|hair|hamburg|hangout|haus|hbo|hdfc|hdfcbank|health|healthcare|help|helsinki|here|hermes|hgtv|hiphop|hisamitsu|hitachi|hiv|hk|hkt|hm|hn|hockey|holdings|holiday|homedepot|homegoods|homes|homesense|honda|horse|hospital|host|hosting|hot|hoteles|hotels|hotmail|house|how|hr|hsbc|ht|hu|hughes|hyatt|hyundai|ibm|icbc|ice|icu|id|ie|ieee|ifm|ikano|il|im|imamat|imdb|immo|immobilien|in|inc|industries|infiniti|info|ing|ink|institute|insurance|insure|int|intel|international|intuit|investments|io|ipiranga|iq|ir|irish|is|iselect|ismaili|ist|istanbul|it|itau|itv|iveco|jaguar|java|jcb|jcp|je|jeep|jetzt|jewelry|jio|jll|jm|jmp|jnj|jo|jobs|joburg|jot|joy|jp|jpmorgan|jprs|juegos|juniper|kaufen|kddi|ke|kerryhotels|kerrylogistics|kerryproperties|kfh|kg|kh|ki|kia|kim|kinder|kindle|kitchen|kiwi|km|kn|koeln|komatsu|kosher|kp|kpmg|kpn|kr|krd|kred|kuokgroup|kw|ky|kyoto|kz|la|lacaixa|ladbrokes|lamborghini|lamer|lancaster|lancia|lancome|land|landrover|lanxess|lasalle|lat|latino|latrobe|law|lawyer|lb|lc|lds|lease|leclerc|lefrak|legal|lego|lexus|lgbt|li|liaison|lidl|life|lifeinsurance|lifestyle|lighting|like|lilly|limited|limo|lincoln|linde|link|lipsy|live|living|lixil|lk|llc|loan|loans|locker|locus|loft|lol|london|lotte|lotto|love|lpl|lplfinancial|lr|ls|lt|ltd|ltda|lu|lundbeck|lupin|luxe|luxury|lv|ly|ma|macys|madrid|maif|maison|makeup|man|management|mango|map|market|marketing|markets|marriott|marshalls|maserati|mattel|mba|mc|mckinsey|md|me|med|media|meet|melbourne|meme|memorial|men|menu|merckmsd|metlife|mg|mh|miami|microsoft|mil|mini|mint|mit|mitsubishi|mk|ml|mlb|mls|mm|mma|mn|mo|mobi|mobile|mobily|moda|moe|moi|mom|monash|money|monster|mopar|mormon|mortgage|moscow|moto|motorcycles|mov|movie|movistar|mp|mq|mr|ms|msd|mt|mtn|mtr|mu|museum|mutual|mv|mw|mx|my|mz|na|nab|nadex|nagoya|name|nationwide|natura|navy|nba|nc|ne|nec|net|netbank|netflix|network|neustar|new|newholland|news|next|nextdirect|nexus|nf|nfl|ng|ngo|nhk|ni|nico|nike|nikon|ninja|nissan|nissay|nl|no|nokia|northwesternmutual|norton|now|nowruz|nowtv|np|nr|nra|nrw|ntt|nu|nyc|nz|obi|observer|off|office|okinawa|olayan|olayangroup|oldnavy|ollo|om|omega|one|ong|onl|online|onyourside|ooo|open|oracle|orange|org|organic|origins|osaka|otsuka|ott|ovh|pa|page|panasonic|paris|pars|partners|parts|party|passagens|pay|pccw|pe|pet|pf|pfizer|pg|ph|pharmacy|phd|philips|phone|photo|photography|photos|physio|piaget|pics|pictet|pictures|pid|pin|ping|pink|pioneer|pizza|pk|pl|place|play|playstation|plumbing|plus|pm|pn|pnc|pohl|poker|politie|porn|post|pr|pramerica|praxi|press|prime|pro|prod|productions|prof|progressive|promo|properties|property|protection|pru|prudential|ps|pt|pub|pw|pwc|py|qa|qpon|quebec|quest|qvc|racing|radio|raid|re|read|realestate|realtor|realty|recipes|red|redstone|redumbrella|rehab|reise|reisen|reit|reliance|ren|rent|rentals|repair|report|republican|rest|restaurant|review|reviews|rexroth|rich|richardli|ricoh|rightathome|ril|rio|rip|rmit|ro|rocher|rocks|rodeo|rogers|room|rs|rsvp|ru|rugby|ruhr|run|rw|rwe|ryukyu|sa|saarland|safe|safety|sakura|sale|salon|samsclub|samsung|sandvik|sandvikcoromant|sanofi|sap|sarl|sas|save|saxo|sb|sbi|sbs|sc|sca|scb|schaeffler|schmidt|scholarships|school|schule|schwarz|science|scjohnson|scor|scot|sd|se|search|seat|secure|security|seek|select|sener|services|ses|seven|sew|sex|sexy|sfr|sg|sh|shangrila|sharp|shaw|shell|shia|shiksha|shoes|shop|shopping|shouji|show|showtime|shriram|si|silk|sina|singles|site|sj|sk|ski|skin|sky|skype|sl|sling|sm|smart|smile|sn|sncf|so|soccer|social|softbank|software|sohu|solar|solutions|song|sony|soy|space|sport|spot|spreadbetting|sr|srl|srt|ss|st|stada|staples|star|starhub|statebank|statefarm|stc|stcgroup|stockholm|storage|store|stream|studio|study|style|su|sucks|supplies|supply|support|surf|surgery|suzuki|sv|swatch|swiftcover|swiss|sx|sy|sydney|symantec|systems|sz|tab|taipei|talk|taobao|target|tatamotors|tatar|tattoo|tax|taxi|tc|tci|td|tdk|team|tech|technology|tel|telefonica|temasek|tennis|teva|tf|tg|th|thd|theater|theatre|tiaa|tickets|tienda|tiffany|tips|tires|tirol|tj|tjmaxx|tjx|tk|tkmaxx|tl|tm|tmall|tn|to|today|tokyo|tools|top|toray|toshiba|total|tours|town|toyota|toys|tr|trade|trading|training|travel|travelchannel|travelers|travelersinsurance|trust|trv|tt|tube|tui|tunes|tushu|tv|tvs|tw|tz|ua|ubank|ubs|uconnect|ug|uk|unicom|university|uno|uol|ups|us|uy|uz|va|vacations|vana|vanguard|vc|ve|vegas|ventures|verisign|versicherung|vet|vg|vi|viajes|video|vig|viking|villas|vin|vip|virgin|visa|vision|vistaprint|viva|vivo|vlaanderen|vn|vodka|volkswagen|volvo|vote|voting|voto|voyage|vu|vuelos|wales|walmart|walter|wang|wanggou|warman|watch|watches|weather|weatherchannel|webcam|weber|website|wed|wedding|weibo|weir|wf|whoswho|wien|wiki|williamhill|win|windows|wine|winners|wme|wolterskluwer|woodside|work|works|world|wow|ws|wtc|wtf|xbox|xerox|xfinity|xihuan|xin|xn--11b4c3d|xn--1ck2e1b|xn--1qqw23a|xn--2scrj9c|xn--30rr7y|xn--3bst00m|xn--3ds443g|xn--3e0b707e|xn--3hcrj9c|xn--3oq18vl8pn36a|xn--3pxu8k|xn--42c2d9a|xn--45br5cyl|xn--45brj9c|xn--45q11c|xn--4gbrim|xn--54b7fta0cc|xn--55qw42g|xn--55qx5d|xn--5su34j936bgsg|xn--5tzm5g|xn--6frz82g|xn--6qq986b3xl|xn--80adxhks|xn--80ao21a|xn--80aqecdr1a|xn--80asehdb|xn--80aswg|xn--8y0a063a|xn--90a3ac|xn--90ae|xn--90ais|xn--9dbq2a|xn--9et52u|xn--9krt00a|xn--b4w605ferd|xn--bck1b9a5dre4c|xn--c1avg|xn--c2br7g|xn--cck2b3b|xn--cg4bki|xn--clchc0ea0b2g2a9gcd|xn--czr694b|xn--czrs0t|xn--czru2d|xn--d1acj3b|xn--d1alf|xn--e1a4c|xn--eckvdtc9d|xn--efvy88h|xn--estv75g|xn--fct429k|xn--fhbei|xn--fiq228c5hs|xn--fiq64b|xn--fiqs8s|xn--fiqz9s|xn--fjq720a|xn--flw351e|xn--fpcrj9c3d|xn--fzc2c9e2c|xn--fzys8d69uvgm|xn--g2xx48c|xn--gckr3f0f|xn--gecrj9c|xn--gk3at1e|xn--h2breg3eve|xn--h2brj9c|xn--h2brj9c8c|xn--hxt814e|xn--i1b6b1a6a2e|xn--imr513n|xn--io0a7i|xn--j1aef|xn--j1amh|xn--j6w193g|xn--jlq61u9w7b|xn--jvr189m|xn--kcrx77d1x4a|xn--kprw13d|xn--kpry57d|xn--kpu716f|xn--kput3i|xn--l1acc|xn--lgbbat1ad8j|xn--mgb9awbf|xn--mgba3a3ejt|xn--mgba3a4f16a|xn--mgba7c0bbn0a|xn--mgbaakc7dvf|xn--mgbaam7a8h|xn--mgbab2bd|xn--mgbah1a3hjkrd|xn--mgbai9azgqp6j|xn--mgbayh7gpa|xn--mgbb9fbpob|xn--mgbbh1a|xn--mgbbh1a71e|xn--mgbc0a9azcg|xn--mgbca7dzdo|xn--mgberp4a5d4ar|xn--mgbgu82a|xn--mgbi4ecexp|xn--mgbpl2fh|xn--mgbt3dhd|xn--mgbtx2b|xn--mgbx4cd0ab|xn--mix891f|xn--mk1bu44c|xn--mxtq1m|xn--ngbc5azd|xn--ngbe9e0a|xn--ngbrx|xn--node|xn--nqv7f|xn--nqv7fs00ema|xn--nyqy26a|xn--o3cw4h|xn--ogbpf8fl|xn--otu796d|xn--p1acf|xn--p1ai|xn--pbt977c|xn--pgbs0dh|xn--pssy2u|xn--q9jyb4c|xn--qcka1pmc|xn--qxam|xn--rhqv96g|xn--rovu88b|xn--rvc1e0am3e|xn--s9brj9c|xn--ses554g|xn--t60b56a|xn--tckwe|xn--tiq49xqyj|xn--unup4y|xn--vermgensberater-ctb|xn--vermgensberatung-pwb|xn--vhquv|xn--vuq861b|xn--w4r85el8fhu5dnra|xn--w4rs40l|xn--wgbh1c|xn--wgbl6a|xn--xhq521b|xn--xkc2al3hye2a|xn--xkc2dl3a5ee0h|xn--y9a3aq|xn--yfro4i67o|xn--ygbi2ammx|xn--zfr164b|xxx|xyz|yachts|yahoo|yamaxun|yandex|ye|yodobashi|yoga|yokohama|you|youtube|yt|yun|za|zappos|zara|zero|zip|zm|zone|zuerich|zw)\"],\"replace\":[\"[\\\"“”]\",\"\\\\[.*\\\\]|【.*】|\\\\{.*\\\\}|\\\\<.*\\\\>|《.*》\",\"([^\\\\d]|^)\\\\d{2}[\\\\/\\\\-.年月日]\\\\d{2}[\\\\/\\\\-.年月日]\\\\d{4}[年月日]?([^\\\\d]|\$)\"]}}"
        private const val L_EN_JSON =
                "{\"digits_\":\"(?!\\\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs))(\\\\d{___V:min_digits_length___,___V:max_digits_length___})\",\"version\":1,\"defaults\":{\"min_ccl\":4,\"max_ccl\":8,\"min_csl\":3,\"max_csl\":6},\"atoms\":{\"dc\":\"\\\\d{___V:min_ccl___,___V:max_ccl___}\",\"ds\":\"\\\\d{___V:min_csl___,___V:max_csl___}\",\"anc\":\"[a-zA-Z0-9]{___V:min_ccl___,___V:max_ccl___}\",\"ans\":\"[a-zA-Z0-9]{___V:min_csl___,___V:max_csl___}\"},\"filtered\":[\"(\\\\d{1,8}(?:\\\\.\\\\d{1,3})?\\\\s*[kmgt]?b(?:ytes?)?)\",\"\\\\d+ (?:sec(?:ond)?s?|min(?:ute)?s?|hours?|hrs)\"],\"components\":{\"digits\":\"(___A:ds___[-\\\\s]___A:ds___[-\\\\s]___A:ds___|___A:ds___[-\\\\s]___A:ds___|___A:dc___)\",\"alphanumeric\":\"(___A:ans___[-\\\\s]___A:ans___[-\\\\s]___A:ans___|___A:ans___[-\\\\s]___A:ans___|___A:anc___)\",\"modifiers\":\"(?:otp|verification|security|auth(?:entication)?|login|identification|sign\\\\-?in)(?:\\\\s+code)?\"},\"templates\":[\".*?___C:modifiers___.\\\\s*(?:is|:|：|\\\\s*)\\\\s*___C:digits___.*\",\".*?(?:code|password|pin)\\\\s*(?:is|:|：|\\\\s*)\\\\s*___C:digits___.*\",\".*?(?:use|enter|paste)\\\\s*(?::|：|\\\\s*)\\\\s*___C:digits___.*\",\".*?___C:digits___(?:\\\\s*(?:is|as)).*?___C:modifiers___.*\",\".*?___C:modifiers___.*?(?:is|:|：|\\\\s*)\\\\s*___C:digits___.*\",\".*?___C:digits___.*?___C:modifiers___.*\",\".*?___C:modifiers___.\\\\s*(?:is|:|：|\\\\s*)\\\\s*___C:alphanumeric___.*\",\".*?(?:use|enter|paste)\\\\s*(?::|：|\\\\s*)\\\\s*___C:alphanumeric___.*\",\".*?(?:code|password|pin).*?(?:is|:|：|\\\\s*)\\\\s*___C:digits___.*\",\".*?___C:alphanumeric___(?:\\\\s*(?:is|as)).*?___C:modifiers___.*\",\".*?___C:alphanumeric___.*?___C:modifiers___.*\"]}"
        private const val L_ZH_JSON =
                "{\"version\":1,\"defaults\":{\"min_ccl\":4,\"max_ccl\":8,\"min_csl\":3,\"max_csl\":6},\"atoms\":{\"dc\":\"\\\\d{___V:min_ccl___,___V:max_ccl___}\",\"ds\":\"\\\\d{___V:min_csl___,___V:max_csl___}\",\"anc\":\"[a-zA-Z0-9]{___V:min_ccl___,___V:max_ccl___}\",\"ans\":\"[a-zA-Z0-9]{___V:min_csl___,___V:max_csl___}\"},\"components\":{\"digits\":\"(___A:dc___|[-\\\\s]___A:ds___[-\\\\s]___A:ds___|___A:ds___[-\\\\s]___A:ds___)\",\"alphanumeric\":\"(___A:anc___|___A:ans___[-\\\\s]___A:ans___[-\\\\s]___A:ans___|___A:ans___[-\\\\s]___A:ans___)\",\"modifiers\":\"(?:(?:[認驗]證|[认验]证|校[檢检验驗]|安全|登[錄录入]|密|身份|[確确][認认]|pin\\\\s*)\\\\s*[编号編號]?\\\\s*[码碼])\",\"aux\":\"(?:[为為是：:])\",\"actions\":\"(?:使用|[輸输]入|粘[贴貼]|[複复][製制])\",\"negative_prefixes\":\"(?:(?:手机|[卡尾])[号號]|卡|[热熱][线線]|[电電][话話]|回[复覆復]|[拨撥]打|[撥拨呼]叫)\",\"negative_suffixes\":\"(?:套餐|[会會][员員]|[计計][划劃]|[产產]品|方案)\",\"negative_surrounding_modifiers\":\"\"},\"filtered\":[\"(\\\\d{1,8}(?:\\\\.\\\\d{1,3})?\\\\s*[kmgt]?b(?:ytes?)?)\",\"(___C:negative_prefixes___\\\\s*___C:aux___?\\\\s*___A:anc___)\",\"(___A:anc___\\\\s*___C:negative_suffixes___)\",\"(\\\\*___A:anc___)\",\"(___A:anc___\\\\*)\"],\"templates\":[\".*?___C:digits___\\\\s*___C:aux___.*?___C:modifiers___.*\",\".*?___C:alphanumeric___\\\\s*___C:aux___.*?___C:modifiers___.*\",\".*?___C:modifiers___\\\\s*___C:aux___\\\\s*___C:digits___.*\",\".*?___C:modifiers___\\\\s*___C:aux___\\\\s*___C:alphanumeric___.*\",\".*?___C:digits___\\\\s*___C:aux___\\\\s*___C:modifiers___.*\",\".*?___C:alphanumeric___\\\\s*___C:aux___\\\\s*___C:modifiers___.*\",\".*?___C:digits___\\\\s*[（(].*?___C:modifiers___.*?[）)].*\",\".*?___C:alphanumeric___\\\\s*[（(].*?___C:modifiers___.*?[）)].*\",\".*?___C:modifiers___.*?___C:digits___.*\",\".*?___C:modifiers___.*?___C:alphanumeric___.*\",\".*?___C:actions___.*?[:：]?\\\\s*___C:digits___.*\",\".*?___C:actions___.*?[:：]?\\\\s*___C:alphanumeric___.*\"]}"
        private const val L_JA_JSON =
                "{\"version\":1,\"defaults\":{\"min_ccl\":4,\"max_ccl\":8,\"min_csl\":3,\"max_csl\":6},\"atoms\":{\"dc\":\"\\\\d{___V:min_ccl___,___V:max_ccl___}\",\"ds\":\"\\\\d{___V:min_csl___,___V:max_csl___}\",\"anc\":\"[a-zA-Z0-9]{___V:min_ccl___,___V:max_ccl___}\",\"ans\":\"[a-zA-Z0-9]{___V:min_csl___,___V:max_csl___}\"},\"filtered\":[\"(\\\\d{1,8}(?:\\\\.\\\\d{1,3})?\\\\s*[kmgt]?b(?:ytes?)?)\"],\"components\":{\"digits\":\"(___A:dc___|___A:ds___[-\\\\s]___A:ds___[-\\\\s]___A:ds___|___A:ds___[-\\\\s]___A:ds___)\",\"modifiers\":\"(?:認証|セキュリティー?|pin\\\\s*)\",\"code\":\"(?:コード|番号)\",\"aux\":\"(?:[はが：:])\",\"actions\":\"(?:入力|貼り付け|コーピ|切り取り|ペースト)\"},\"templates\":[\".*?___C:digits___\\\\s*___C:aux___.*?___C:modifiers___\\\\s*___C:code___.*\",\".*?___C:modifiers___\\\\s*___C:code___.*?___C:aux___\\\\s*___C:digits___.*\",\".*?___C:digits___\\\\s*___C:aux___.*?___C:code___.*\",\".*?___C:code___.*?___C:aux___\\\\s*___C:digits___.*\",\".*?___C:digits___.*?___C:modifiers___\\\\s*___C:code___.*\",\".*?___C:modifiers___\\\\s*___C:code___.*?___C:digits___.*\",\".*?___C:actions___.*?[:：]?\\\\s*___C:digits___.*\"]}"

        fun find(@Suppress("UNUSED_PARAMETER") context: Context, input: String): String? {
            val instance = CodeauxLibPortable()
            return instance.find(input)
        }
    }

    class RegexPipeline {
        companion object {
            fun with(input: String, recipe: RegexRecipe): String? {
                try {
                    var filteredInput = input
                    recipe.filteredExpressions.forEach { expression ->
                        filteredInput = filteredInput.replace(Regex(expression, setOf(RegexOption.IGNORE_CASE)), "")
                    }
                    recipe.expressions.forEach { expression ->
                        val r = Regex(expression, setOf(RegexOption.IGNORE_CASE))
                        val result = r.find(filteredInput)
                        if (result != null) {
                            return result.groupValues[1].replace(Regex("[\\s-]", setOf(RegexOption.IGNORE_CASE)), "")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    return null
                }
                return null
            }
        }
    }

    class RegexRecipe private constructor() {
        val expressions = ArrayList<String>()
        val filteredExpressions = ArrayList<String>()

        companion object {
            private const val REGEX_MACRO_PATTERN = "___([ACV]):(\\w*?)___"

            fun fromJson(jsonObject: JSONObject, overrideVariables: Map<String, String>? = null): RegexRecipe {
                val recipe = RegexRecipe()
                val variables = HashMap<String, String>()
                val components = HashMap<String, String>()
                val atoms = HashMap<String, String>()
                val emptyMap = HashMap<String, String>()

                val defaults = jsonObject.getJSONObject("defaults")
                defaults.keys().forEach { key ->
                    variables[key] = defaults.getString(key)
                }
                if (overrideVariables != null) {
                    for ((k, v) in overrideVariables) {
                        variables[k] = v
                    }
                }

                val atomsUnprocessed = jsonObject.getJSONObject("atoms")
                atomsUnprocessed.keys().forEach { key ->
                    atoms[key] = processMacros(
                            atomsUnprocessed.getString(key),
                            variables,
                            emptyMap,
                            emptyMap
                    )
                }

                val componentsUnprocessed = jsonObject.getJSONObject("components")
                componentsUnprocessed.keys().forEach { key ->
                    components[key] = processMacros(
                            componentsUnprocessed.getString(key),
                            variables,
                            atoms,
                            emptyMap
                    )
                }

                val templates = jsonObject.getJSONArray("templates")
                for (i in 0 until templates.length()) {
                    recipe.expressions.add(
                            processMacros(
                                    templates.getString(i),
                                    variables,
                                    atoms,
                                    components
                            )
                    )
                }

                val filtered = jsonObject.getJSONArray("filtered")
                for (i in 0 until filtered.length()) {
                    recipe.filteredExpressions.add(
                            processMacros(
                                    filtered.getString(i),
                                    variables,
                                    atoms,
                                    components
                            )
                    )
                }
                return recipe
            }

            private fun processMacros(
                    input: String,
                    variables: Map<String, String>,
                    atoms: Map<String, String>,
                    components: Map<String, String>
            ): String {
                var text = input
                val macroRegex = Regex(REGEX_MACRO_PATTERN)
                var result: MatchResult? = null
                while ({ result = macroRegex.find(text);result != null }()) {
                    val macroType = result!!.groupValues[1]
                    val macroName = result!!.groupValues[2]

                    val getMacroExtras = {
                        when (macroType) {
                            "A" -> atoms
                            "C" -> components
                            "V" -> variables
                            else -> throw Exception("Undefined macro type `$macroType` found in `$input`")
                        }
                    }

                    if (!getMacroExtras().containsKey(macroName)) {
                        throw Exception("Undefined macro name `$macroName` found in `$input`")
                    }
                    text = text.replaceFirst(macroRegex, Regex.escapeReplacement(getMacroExtras().getValue(macroName)))
                }
                return text
            }
        }
    }
}