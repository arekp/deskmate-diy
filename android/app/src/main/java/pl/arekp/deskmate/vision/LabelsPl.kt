package pl.arekp.deskmate.vision

/**
 * Polskie nazwy najczęstszych etykiet modelu ML Kit Image Labeling (model podstawowy, ~400 etykiet).
 * Nieznane etykiety zostają po angielsku – model AI i tak je zrozumie.
 */
object LabelsPl {

    fun translate(label: String): String = MAP[label.trim().lowercase()] ?: label

    private val MAP = mapOf(
        // ludzie i wygląd
        "person" to "osoba", "smile" to "uśmiech", "selfie" to "selfie", "hand" to "dłoń", "beard" to "broda",
        "hair" to "włosy", "glasses" to "okulary", "sunglasses" to "okulary przeciwsłoneczne", "hat" to "kapelusz",
        "cap" to "czapka", "eyelash" to "rzęsy", "ear" to "ucho", "nail" to "paznokieć", "tattoo" to "tatuaż",
        "baby" to "niemowlę", "child" to "dziecko", "crowd" to "tłum", "team" to "zespół",
        // ubrania
        "jacket" to "kurtka", "jeans" to "dżinsy", "shirt" to "koszula", "sleeve" to "rękaw", "dress" to "sukienka",
        "scarf" to "szalik", "shoe" to "but", "sneakers" to "trampki", "tie" to "krawat", "bag" to "torba",
        "handbag" to "torebka", "backpack" to "plecak", "watch" to "zegarek", "jewelry" to "biżuteria",
        // biurko i elektronika
        "computer" to "komputer", "laptop" to "laptop", "monitor" to "monitor", "screen" to "ekran",
        "television" to "telewizor", "mobile phone" to "telefon", "cell phone" to "telefon", "keyboard" to "klawiatura",
        "mouse" to "mysz", "headphones" to "słuchawki", "camera" to "aparat", "desk" to "biurko", "table" to "stół",
        "chair" to "krzesło", "couch" to "kanapa", "sofa" to "kanapa", "bed" to "łóżko", "pillow" to "poduszka",
        "shelf" to "półka", "bookcase" to "regał", "lamp" to "lampa", "curtain" to "zasłona", "window" to "okno",
        "door" to "drzwi", "room" to "pokój", "kitchen" to "kuchnia", "bathroom" to "łazienka", "sink" to "zlew",
        "mirror" to "lustro", "clock" to "zegar", "paper" to "papier", "book" to "książka", "comics" to "komiks",
        "pen" to "długopis", "poster" to "plakat", "painting" to "obraz", "toy" to "zabawka", "balloon" to "balon",
        "musical instrument" to "instrument muzyczny", "guitar" to "gitara", "piano" to "pianino",
        // jedzenie i naczynia
        "cup" to "kubek", "mug" to "kubek", "coffee" to "kawa", "tea" to "herbata", "bottle" to "butelka",
        "glass" to "szklanka", "wine" to "wino", "beer" to "piwo", "plate" to "talerz", "tableware" to "naczynia",
        "food" to "jedzenie", "fruit" to "owoc", "vegetable" to "warzywo", "bread" to "chleb", "cake" to "ciasto",
        "cookie" to "ciastko", "pizza" to "pizza", "sandwich" to "kanapka", "cheeseburger" to "cheeseburger",
        "pasta" to "makaron", "salad" to "sałatka", "dessert" to "deser", "candy" to "cukierek", "egg" to "jajko",
        // zwierzęta i rośliny
        "cat" to "kot", "dog" to "pies", "bird" to "ptak", "fish" to "ryba", "horse" to "koń", "insect" to "owad",
        "butterfly" to "motyl", "pet" to "zwierzak", "plant" to "roślina", "flowerpot" to "doniczka",
        "flower" to "kwiat", "tree" to "drzewo", "grass" to "trawa", "garden" to "ogród", "leaf" to "liść",
        // na zewnątrz
        "sky" to "niebo", "cloud" to "chmura", "sun" to "słońce", "building" to "budynek", "house" to "dom",
        "car" to "samochód", "bicycle" to "rower", "road" to "droga", "street" to "ulica", "park" to "park",
        "beach" to "plaża", "mountain" to "góra", "lake" to "jezioro", "river" to "rzeka", "snow" to "śnieg",
        // ogólne
        "fun" to "zabawa", "cool" to "fajne", "sitting" to "siedzenie", "standing" to "stanie",
        "darkness" to "ciemność", "light" to "światło", "metal" to "metal", "wood" to "drewno", "plastic" to "plastik",
        "textile" to "tkanina", "pattern" to "wzór", "sport" to "sport", "game" to "gra", "party" to "impreza",
        "christmas" to "święta", "flag" to "flaga", "money" to "pieniądze", "rainbow" to "tęcza",
    )
}
