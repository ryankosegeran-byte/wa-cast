package net.myryzz.wacast.core

data class EmojiItem(
    val char: String,
    val name: String,
    val kw: String
)

data class EmojiCategory(
    val id: String,
    val name: String,
    val emojis: List<EmojiItem>
)

val DEFAULT_RECENT_EMOJIS = listOf("👋", "🙏", "⏰", "📋", "💰", "📅", "✅", "✨")

val EMOJI_CATEGORIES = listOf(
    EmojiCategory(
        id = "smileys",
        name = "Senyum & Gerakan",
        emojis = listOf(
            EmojiItem("😀", "Grinning face", "smile happy senang senyum tertawa"),
            EmojiItem("😃", "Grinning face with big eyes", "smile happy gembira senang"),
            EmojiItem("😄", "Grinning face with smiling eyes", "happy laugh senang tawa"),
            EmojiItem("😁", "Beaming face", "grin smile cengir senang"),
            EmojiItem("😅", "Grinning sweat", "nervous relief keringat gugup"),
            EmojiItem("😂", "Face with tears of joy", "laugh funny ngakak ketawa lucu"),
            EmojiItem("🤣", "Rolling on the floor laughing", "laugh rofl ngakak"),
            EmojiItem("😊", "Smiling face with smiling eyes", "warm blush ramah senyum manis"),
            EmojiItem("😇", "Smiling face with halo", "angel good baik suci"),
            EmojiItem("🙂", "Slightly smiling face", "smile polite ramah senyum"),
            EmojiItem("😉", "Winking face", "wink kedip"),
            EmojiItem("😌", "Relieved face", "calm relief lega tenang"),
            EmojiItem("😍", "Heart eyes", "love adore cinta suka"),
            EmojiItem("🥰", "Smiling face with hearts", "love warm sayang cinta"),
            EmojiItem("😘", "Blowing a kiss", "kiss love cium sayang"),
            EmojiItem("😋", "Savoring food", "yum delicious enak lezat"),
            EmojiItem("😎", "Smiling face with sunglasses", "cool keren santai"),
            EmojiItem("🤩", "Star-struck", "wow excited kagum keren"),
            EmojiItem("🥳", "Partying face", "party celebrate selamat pesta ulang tahun"),
            EmojiItem("😏", "Smirking face", "smirk senyum miring"),
            EmojiItem("😔", "Pensive face", "sad sorry maaf sedih"),
            EmojiItem("🥺", "Pleading face", "please puppy eyes mohon"),
            EmojiItem("😢", "Crying face", "sad tear sedih nangis"),
            EmojiItem("😭", "Loudly crying face", "sob sad sedih nangis"),
            EmojiItem("😤", "Face with steam from nose", "frustrated hmph kesal"),
            EmojiItem("😠", "Angry face", "mad angry marah"),
            EmojiItem("🤯", "Exploding head", "mind blown shocked kaget"),
            EmojiItem("😳", "Flushed face", "embarrassed shy malu kaget"),
            EmojiItem("😱", "Face screaming in fear", "omg shocked panik kaget"),
            EmojiItem("🤗", "Hugging face", "hug welcome peluk ramah"),
            EmojiItem("🤔", "Thinking face", "hmm think mikir tanya"),
            EmojiItem("🤭", "Face with hand over mouth", "oops giggle ups"),
            EmojiItem("🤫", "Shushing face", "quiet secret diam rahasia"),
            EmojiItem("😶", "Face without mouth", "silent diam"),
            EmojiItem("😐", "Neutral face", "meh datar biasa"),
            EmojiItem("🙄", "Face with rolling eyes", "eyeroll malas"),
            EmojiItem("😴", "Sleeping face", "sleep tired tidur ngantuk"),
            EmojiItem("😷", "Face with medical mask", "sick mask sakit masker"),
            EmojiItem("🤒", "Face with thermometer", "fever sick demam sakit"),
            EmojiItem("👋", "Waving hand", "hello hi salam halo hai"),
            EmojiItem("🤚", "Raised back of hand", "stop hand tangan"),
            EmojiItem("✋", "Raised hand", "stop high five tangan"),
            EmojiItem("👌", "OK hand", "ok good oke sip mantap"),
            EmojiItem("🤌", "Pinched fingers", "chef kiss gesture"),
            EmojiItem("✌️", "Victory hand", "peace two damai"),
            EmojiItem("🤞", "Crossed fingers", "luck hope harap semoga"),
            EmojiItem("🫶", "Heart hands", "love thanks terima kasih cinta"),
            EmojiItem("👉", "Backhand index pointing right", "point right lihat klik lanjut"),
            EmojiItem("👇", "Backhand index pointing down", "point down bawah link"),
            EmojiItem("👆", "Backhand index pointing up", "point up atas"),
            EmojiItem("👍", "Thumbs up", "like ok yes setuju sip bagus mantap"),
            EmojiItem("👎", "Thumbs down", "dislike no tidak"),
            EmojiItem("👏", "Clapping hands", "clap bravo tepuk tangan hebat"),
            EmojiItem("🙌", "Raising hands", "hooray celebrate syukur mantap"),
            EmojiItem("🤝", "Handshake", "deal agree kerja sama sepakat salam"),
            EmojiItem("🙏", "Folded hands", "please thanks mohon maaf terima kasih makasih salam"),
            EmojiItem("💪", "Flexed biceps", "strong semangat kuat")
        )
    ),
    EmojiCategory(
        id = "nature",
        name = "Hewan & Alam",
        emojis = listOf(
            EmojiItem("🐶", "Dog face", "dog puppy anjing"),
            EmojiItem("🐱", "Cat face", "cat kitty kucing"),
            EmojiItem("🐰", "Rabbit face", "bunny kelinci"),
            EmojiItem("🐻", "Bear", "bear beruang"),
            EmojiItem("🐼", "Panda", "panda"),
            EmojiItem("🦊", "Fox", "fox rubah"),
            EmojiItem("🦁", "Lion", "lion singa"),
            EmojiItem("🐮", "Cow face", "cow sapi"),
            EmojiItem("🐸", "Frog", "frog kodok katak"),
            EmojiItem("🐵", "Monkey face", "monkey monyet"),
            EmojiItem("🐔", "Chicken", "chicken ayam"),
            EmojiItem("🐧", "Penguin", "penguin pinguin"),
            EmojiItem("🦋", "Butterfly", "butterfly kupu kupu"),
            EmojiItem("🐝", "Honeybee", "bee lebah madu"),
            EmojiItem("🐢", "Turtle", "turtle kura kura"),
            EmojiItem("🐳", "Spouting whale", "whale paus"),
            EmojiItem("🐬", "Dolphin", "dolphin lumba"),
            EmojiItem("🌱", "Seedling", "plant grow tunas tumbuh"),
            EmojiItem("🌿", "Herb", "leaf green daun hijau"),
            EmojiItem("🍀", "Four leaf clover", "luck daun semanggi untung"),
            EmojiItem("🌸", "Cherry blossom", "flower pink bunga sakura"),
            EmojiItem("🌺", "Hibiscus", "flower bunga"),
            EmojiItem("🌻", "Sunflower", "sunflower bunga matahari"),
            EmojiItem("🌹", "Rose", "rose flower mawar bunga"),
            EmojiItem("☀️", "Sun", "sun morning pagi cerah matahari"),
            EmojiItem("🌤️", "Sun behind small cloud", "weather siang cerah berawan"),
            EmojiItem("🌙", "Crescent moon", "moon night malam bulan ramadan"),
            EmojiItem("🌈", "Rainbow", "rainbow pelangi")
        )
    ),
    EmojiCategory(
        id = "food",
        name = "Makanan & Minuman",
        emojis = listOf(
            EmojiItem("🍎", "Red apple", "apple fruit apel buah"),
            EmojiItem("🍊", "Tangerine", "orange jeruk buah"),
            EmojiItem("🍋", "Lemon", "lemon jeruk"),
            EmojiItem("🍉", "Watermelon", "watermelon semangka"),
            EmojiItem("🍓", "Strawberry", "strawberry stroberi"),
            EmojiItem("🥭", "Mango", "mango mangga"),
            EmojiItem("🥑", "Avocado", "avocado alpukat"),
            EmojiItem("🍞", "Bread", "bread roti"),
            EmojiItem("🥐", "Croissant", "pastry roti sarapan"),
            EmojiItem("🍔", "Hamburger", "burger makan"),
            EmojiItem("🍟", "French fries", "fries kentang"),
            EmojiItem("🍕", "Pizza", "pizza makan"),
            EmojiItem("🍜", "Steaming bowl", "noodle ramen bakmi mie"),
            EmojiItem("🍛", "Curry rice", "rice curry nasi kari"),
            EmojiItem("🍣", "Sushi", "sushi jepang"),
            EmojiItem("🍱", "Bento box", "lunch box makan siang"),
            EmojiItem("🍚", "Cooked rice", "rice nasi"),
            EmojiItem("🍩", "Doughnut", "donut donat manis"),
            EmojiItem("🍪", "Cookie", "cookie kue biskuit"),
            EmojiItem("🎂", "Birthday cake", "cake birthday kue ulang tahun"),
            EmojiItem("🍰", "Shortcake", "cake kue dessert"),
            EmojiItem("🍫", "Chocolate bar", "chocolate coklat"),
            EmojiItem("☕", "Hot beverage", "coffee tea kopi teh santai pagi"),
            EmojiItem("🍵", "Teacup", "tea match teh hangat"),
            EmojiItem("🧋", "Bubble tea", "boba tea minuman"),
            EmojiItem("🥤", "Cup with straw", "drink soda jus minuman"),
            EmojiItem("🥂", "Clinking glasses", "cheers celebrate selamat"),
            EmojiItem("🍽️", "Fork and knife with plate", "meal dinner makan")
        )
    ),
    EmojiCategory(
        id = "travel",
        name = "Aktivitas & Tempat",
        emojis = listOf(
            EmojiItem("⚽", "Soccer ball", "football bola olahraga"),
            EmojiItem("🏀", "Basketball", "basket olahraga"),
            EmojiItem("🎮", "Video game", "game main"),
            EmojiItem("🎯", "Direct hit", "target goal tepat sasaran"),
            EmojiItem("🏆", "Trophy", "win winner piala juara"),
            EmojiItem("🎁", "Wrapped gift", "gift present hadiah kado promo bonus"),
            EmojiItem("🎉", "Party popper", "celebrate congrats selamat sukses"),
            EmojiItem("🎊", "Confetti ball", "party selamat perayaan"),
            EmojiItem("🚗", "Automobile", "car mobil antar"),
            EmojiItem("🛵", "Motor scooter", "ojek kurir motor delivery kirim"),
            EmojiItem("🚚", "Delivery truck", "truck shipping kirim paket ekspedisi"),
            EmojiItem("✈️", "Airplane", "flight travel pesawat terbang"),
            EmojiItem("🚀", "Rocket", "launch fast roket cepat"),
            EmojiItem("🚂", "Locomotive", "train kereta"),
            EmojiItem("🏠", "House", "home house rumah alamat"),
            EmojiItem("🏢", "Office building", "office kantor gedung"),
            EmojiItem("🏪", "Convenience store", "shop store toko outlet"),
            EmojiItem("🏥", "Hospital", "hospital klinik rumah sakit"),
            EmojiItem("🏦", "Bank", "bank transfer rekening"),
            EmojiItem("📍", "Round pushpin", "location map lokasi alamat tempat"),
            EmojiItem("🗺️", "World map", "map peta lokasi"),
            EmojiItem("🏖️", "Beach with umbrella", "holiday vacation libur pantai"),
            EmojiItem("⛰️", "Mountain", "mountain gunung"),
            EmojiItem("🚦", "Traffic light", "traffic lampu jalan")
        )
    ),
    EmojiCategory(
        id = "objects",
        name = "Objek & Alat",
        emojis = listOf(
            EmojiItem("⏰", "Alarm clock", "alarm reminder time waktu jam jatuh tempo"),
            EmojiItem("⏱️", "Stopwatch", "timer fast cepat waktu"),
            EmojiItem("🕰️", "Mantelpiece clock", "clock jam waktu"),
            EmojiItem("⏳", "Hourglass", "wait pending tunggu batas waktu"),
            EmojiItem("📅", "Calendar", "date schedule tanggal jadwal jatuh tempo"),
            EmojiItem("📆", "Tear-off calendar", "date jadwal hari"),
            EmojiItem("📋", "Clipboard", "list detail catatan invoice nota daftar"),
            EmojiItem("🧾", "Receipt", "invoice bill struk tagihan kwitansi nota"),
            EmojiItem("💰", "Money bag", "money total price uang rupiah bayar tagihan"),
            EmojiItem("💵", "Dollar banknote", "cash money tunai uang bayar"),
            EmojiItem("💳", "Credit card", "card payment bayar transfer rekening"),
            EmojiItem("💎", "Gem stone", "vip premium berlian"),
            EmojiItem("🛒", "Shopping cart", "shop order cart keranjang belanja pesanan"),
            EmojiItem("🛍️", "Shopping bags", "shop order belanja promo"),
            EmojiItem("📦", "Package", "parcel box order paket kirim barang"),
            EmojiItem("🏷️", "Label", "price tag diskon promo harga label"),
            EmojiItem("📱", "Mobile phone", "phone whatsapp hp nomor kontak"),
            EmojiItem("💻", "Laptop", "computer internet kerja laptop"),
            EmojiItem("🔗", "Link", "url link tautan bayar"),
            EmojiItem("🔔", "Bell", "alert notify reminder pengingat notifikasi"),
            EmojiItem("📢", "Loudspeaker", "announce broadcast info pengumuman"),
            EmojiItem("📣", "Megaphone", "promo info broadcast pengumuman"),
            EmojiItem("✉️", "Envelope", "mail message pesan surat"),
            EmojiItem("📩", "Envelope with arrow", "inbox send pesan masuk"),
            EmojiItem("📝", "Memo", "note write catatan tulis"),
            EmojiItem("📄", "Page facing up", "document file dokumen berkas"),
            EmojiItem("📊", "Bar chart", "report stats laporan data"),
            EmojiItem("📌", "Pushpin", "pin important penting catatan"),
            EmojiItem("🔒", "Locked", "secure private aman kunci"),
            EmojiItem("🔑", "Key", "key access kunci kode"),
            EmojiItem("💡", "Light bulb", "idea tip info ide tips"),
            EmojiItem("🛠️", "Hammer and wrench", "maintenance service perbaikan gangguan")
        )
    ),
    EmojiCategory(
        id = "symbols",
        name = "Simbol & Tanda",
        emojis = listOf(
            EmojiItem("✅", "Check mark button", "done ok verified selesai lunas benar centang"),
            EmojiItem("☑️", "Check box with check", "check ok selesai centang"),
            EmojiItem("✔️", "Check mark", "check yes centang"),
            EmojiItem("❌", "Cross mark", "no cancel wrong batal gagal silang"),
            EmojiItem("⚠️", "Warning", "alert warning perhatian hati hati"),
            EmojiItem("❗", "Red exclamation mark", "important urgent penting segera"),
            EmojiItem("❓", "Red question mark", "question tanya bantuan"),
            EmojiItem("🚫", "Prohibited", "stop banned dilarang tutup"),
            EmojiItem("✨", "Sparkles", "new promo shine baru spesial kilau"),
            EmojiItem("🔥", "Fire", "hot promo trending laris"),
            EmojiItem("💯", "Hundred points", "100 full mantap sempurna"),
            EmojiItem("⭐", "Star", "star favorite rating bintang"),
            EmojiItem("🌟", "Glowing star", "star special bintang"),
            EmojiItem("⚡", "High voltage", "fast instant kilat cepat listrik"),
            EmojiItem("💥", "Collision", "boom promo ledak"),
            EmojiItem("❤️", "Red heart", "love heart cinta kasih sayang"),
            EmojiItem("💚", "Green heart", "green whatsapp heart hijau suka"),
            EmojiItem("💙", "Blue heart", "blue heart biru"),
            EmojiItem("🧡", "Orange heart", "orange heart oranye"),
            EmojiItem("💜", "Purple heart", "purple heart ungu"),
            EmojiItem("🤍", "White heart", "white heart putih"),
            EmojiItem("➡️", "Right arrow", "next arrow lanjut ke"),
            EmojiItem("⬅️", "Left arrow", "back arrow kembali"),
            EmojiItem("🔴", "Red circle", "red dot merah tutup mati"),
            EmojiItem("🟡", "Yellow circle", "yellow dot kuning proses"),
            EmojiItem("🟢", "Green circle", "green dot aktif online hijau buka"),
            EmojiItem("🔵", "Blue circle", "blue dot biru info"),
            EmojiItem("💬", "Speech balloon", "chat message pesan balas")
        )
    ),
    EmojiCategory(
        id = "flags",
        name = "Bendera",
        emojis = listOf(
            EmojiItem("🇮🇩", "Indonesia", "indonesia id merah putih"),
            EmojiItem("🇲🇾", "Malaysia", "malaysia my"),
            EmojiItem("🇸🇬", "Singapore", "singapore singapura sg"),
            EmojiItem("🇹🇭", "Thailand", "thailand th"),
            EmojiItem("🇵🇭", "Philippines", "philippines filipina ph"),
            EmojiItem("🇻🇳", "Vietnam", "vietnam vn"),
            EmojiItem("🇯🇵", "Japan", "japan jepang jp"),
            EmojiItem("🇰🇷", "South Korea", "korea kr"),
            EmojiItem("🇨🇳", "China", "china cina cn"),
            EmojiItem("🇮🇳", "India", "india in"),
            EmojiItem("🇸🇦", "Saudi Arabia", "saudi arab sa"),
            EmojiItem("🇦🇪", "United Arab Emirates", "uae dubai ae"),
            EmojiItem("🇦🇺", "Australia", "australia au"),
            EmojiItem("🇺🇸", "United States", "usa us america amerika"),
            EmojiItem("🇬🇧", "United Kingdom", "uk gb britain inggris"),
            EmojiItem("🏁", "Chequered flag", "finish selesai bendera")
        )
    )
)

private val EMOJI_BY_CHAR: Map<String, EmojiItem> by lazy {
    val map = HashMap<String, EmojiItem>()
    for (cat in EMOJI_CATEGORIES) {
        for (item in cat.emojis) {
            if (!map.containsKey(item.char)) {
                map[item.char] = item
            }
        }
    }
    map
}

fun lookupEmoji(char: String): EmojiItem =
    EMOJI_BY_CHAR[char] ?: EmojiItem(char, char, "")

fun filterEmojis(query: String?): List<EmojiItem> {
    val q = (query ?: "").trim().lowercase()
    if (q.isEmpty()) return emptyList()
    val out = ArrayList<EmojiItem>()
    for (cat in EMOJI_CATEGORIES) {
        for (item in cat.emojis) {
            if (item.char == q ||
                item.name.lowercase().contains(q) ||
                item.kw.lowercase().contains(q)
            ) {
                out.add(item)
            }
        }
    }
    return out
}
