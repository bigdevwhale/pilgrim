package app.pilgrim.domain

/**
 * What a place of power is. Each kind has its silhouette on the stamp, a radius that counts as
 * "here", and a few lines of how to come in. No symbols of faith anywhere: only buildings and nature.
 */
enum class Kind(val ru: String, val en: String, val radius: Double, val nature: Boolean) {
    CHURCH("Церковь", "Church", 60.0, false),
    MOSQUE("Мечеть", "Mosque", 60.0, false),
    SYNAGOGUE("Синагога", "Synagogue", 50.0, false),
    TEMPLE("Храм", "Temple", 60.0, false),
    SHRINE("Часовня", "Chapel", 35.0, false),
    SPRING("Родник", "Spring", 35.0, true),
    TREE("Старое дерево", "Old tree", 30.0, true),
    STONE("Камень", "Stone", 35.0, true),
    HEIGHT("Высота", "Height", 60.0, true),
    OWN("Своё место", "My place", 40.0, false),
    ;

    fun title(ru: Boolean) = if (ru) this.ru else en

    /**
     * Metres added to the radius when checking "still here": GPS wanders under crowns, in ravines
     * and by rock, so nature gets more room than a building.
     */
    val slack: Double get() = if (nature) 50.0 else 30.0

    /** How to come in, a few short lines. The same even tone for every faith. */
    fun manners(ru: Boolean): List<String> = when (this) {
        CHURCH -> if (ru) listOf(
            "Мужчинам — снять головной убор, женщинам — покрыть голову, если так принято в этом храме.",
            "Плечи и колени закрыты.",
            "Во время службы — у стены, не в проходе. Можно просто сидеть на скамье во дворе.",
        ) else listOf(
            "Men take off their hats; women cover their heads where that is the custom.",
            "Shoulders and knees covered.",
            "During a service, stay by the wall, not in the aisle. The bench in the yard is fine too.",
        )
        MOSQUE -> if (ru) listOf(
            "Обувь — снять у входа, на полке или в пакете.",
            "Женщинам — покрыть голову; всем — закрытые плечи и колени.",
            "Не проходить перед молящимися. Во время намаза лучше посидеть во дворе.",
            "Не уверены — спросите у входа: подскажут.",
        ) else listOf(
            "Shoes off at the door, on the rack or in a bag.",
            "Women cover their heads; everyone covers shoulders and knees.",
            "Don't walk in front of someone praying. During prayer, the courtyard is a good place to sit.",
            "Not sure? Ask at the door: people will tell you.",
        )
        SYNAGOGUE -> if (ru) listOf(
            "Мужчинам — покрыть голову: кипу обычно дают у входа.",
            "На входе бывает охрана — возьмите документ.",
            "В шаббат и праздники телефон не доставайте вовсе — он и так лежит.",
        ) else listOf(
            "Men cover their heads: a kippah is usually offered at the door.",
            "There may be security at the entrance: bring an ID.",
            "On Shabbat and holidays keep the phone out of sight entirely — it's lying down anyway.",
        )
        TEMPLE -> if (ru) listOf(
            "Обувь снимают, если у входа стоит полка с обувью.",
            "Святыни обходят по часовой стрелке.",
            "Не направлять ступни к алтарю, не трогать статуи.",
        ) else listOf(
            "Take your shoes off if there's a shoe rack at the door.",
            "Walk around shrines clockwise.",
            "Don't point your feet at the altar; don't touch the statues.",
        )
        SHRINE -> if (ru) listOf(
            "Часовня маленькая: если внутри кто-то есть — посидите рядом.",
            "Ничего не брать и не оставлять, кроме своего времени.",
        ) else listOf(
            "A chapel is small: if someone is inside, sit nearby.",
            "Take nothing and leave nothing but your time.",
        )
        SPRING -> if (ru) listOf(
            "Ничего не бросать в воду: ни монет, ни лент.",
            "Набрать воды можно; мыть руки и посуду в самом роднике — нет.",
            "Мусор — с собой, даже чужой.",
        ) else listOf(
            "Throw nothing into the water: no coins, no ribbons.",
            "Taking water is fine; washing hands or dishes in the spring itself is not.",
            "Take litter with you, even someone else's.",
        )
        TREE -> if (ru) listOf(
            "Не залезать, не вбивать, не вырезать.",
            "Ленточки не завязывать: они душат ветви.",
            "Сидеть можно и на корнях — бережно.",
        ) else listOf(
            "Don't climb, nail or carve.",
            "Don't tie ribbons: they strangle the branches.",
            "You may sit on the roots — gently.",
        )
        STONE -> if (ru) listOf(
            "Если камень почитают — не залезать на него.",
            "Ничего не выцарапывать и не откалывать на память.",
        ) else listOf(
            "If the stone is revered, don't climb it.",
            "Scratch nothing and chip nothing off as a keepsake.",
        )
        HEIGHT -> if (ru) listOf(
            "Не сходить с тропы.",
            "Лучшее место для вида уступите тому, кто пришёл позже, — у вас десять минут, у него, может быть, одна.",
        ) else listOf(
            "Stay on the path.",
            "Give the best spot to whoever comes after you — you have ten minutes, they may have one.",
        )
        OWN -> if (ru) listOf(
            "Это место — ваше: правила здесь ваши.",
            "Если рядом люди — не мешайте им своей тишиной.",
        ) else listOf(
            "This place is yours: the rules here are yours.",
            "If there are people around, don't make your silence their problem.",
        )
    }

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: OWN

        /** Kinds a person can mark a place as, in the picker's order. */
        val pickable = listOf(SPRING, TREE, STONE, HEIGHT, SHRINE, CHURCH, MOSQUE, SYNAGOGUE, TEMPLE, OWN)
    }
}

/** How a place got into the book. */
enum class Origin {
    /** From the common map, as it is. */
    OSM,

    /** Marked by the person: pencil until three days. */
    OWN,

    /** On the common map, but its point was elsewhere: tied to where it really is. */
    MOVED;

    companion object {
        fun of(s: String?) = entries.firstOrNull { it.name == s } ?: OSM
    }
}
