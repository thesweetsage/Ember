package app.ember.data

/**
 * Tags are stored as "Category::Name" so each one keeps its section.
 * There is no limit on how many a character can have.
 */
object Tags {
    const val SEP = "::"

    data class Category(val name: String, val suggestions: List<String>)

    val categories = listOf(
        Category("Personality", listOf(
            "Dominant", "Possessive", "Protective", "Jealous", "Obsessive", "Brooding", "Cold", "Grumpy",
            "Stoic", "Arrogant", "Cocky", "Charming", "Flirty", "Playful", "Sarcastic", "Teasing",
            "Mysterious", "Intense", "Morally grey", "Villain", "Ruthless", "Tortured", "Soft", "Sweet",
            "Gentle giant", "Golden retriever", "Shy", "Awkward", "Loyal", "Patient", "Calm", "Chaotic",
            "Bad boy", "Gentleman", "Intellectual", "Funny", "Reserved", "Clingy",
        )),
        Category("Setting", listOf(
            "Modern", "Small town", "Big city", "College", "Workplace", "Rural", "Ranch", "Seaside",
            "Mountains", "Arctic", "Desert", "Summer camp", "Church", "On tour", "Road trip", "Hospital",
            "Fantasy", "Medieval", "Historical", "Regency", "Victorian", "Gilded Age", "Wild West",
            "Sci-fi", "Space", "Post-apocalyptic", "Dystopian", "Supernatural", "Paranormal",
            "Werewolves", "Vampires", "Fae", "Witches", "Demons", "Angels", "Ghosts", "Royalty", "Underworld",
        )),
        Category("Occupation", listOf(
            "Priest", "Scientist", "Musician", "Rock band", "Athlete", "BMX rider", "Biker", "Fighter",
            "Soldier", "Bodyguard", "Detective", "Cop", "Doctor", "Firefighter", "Chef", "Artist",
            "Tattoo artist", "Photographer", "Writer", "Professor", "CEO", "Mafia boss", "Hitman",
            "Thief", "Hacker", "Cowboy", "Pilot", "Ghost hunter", "Hunter", "Knight", "Prince", "King",
            "Pirate", "Mechanic", "Lifeguard", "Camp counsellor",
        )),
        Category("Trope & dynamic", listOf(
            "Enemies to lovers", "Rivals to lovers", "Friends to lovers", "Strangers to lovers",
            "Forbidden love", "Slow burn", "Instalove", "Forced proximity", "Only one bed", "Fake dating",
            "Arranged marriage", "Second chance", "Age gap", "Grumpy x sunshine", "Bodyguard romance",
            "Boss x employee", "Best friend's brother", "Love triangle", "Poly", "Reverse harem",
            "Obsession", "Stalker", "Dark romance", "Captive", "Hurt/comfort", "Found family",
            "Protector", "Opposites attract", "Secret relationship", "Online to IRL",
        )),
        Category("Kinks", listOf(
            "Dominance", "Submission", "Praise", "Degradation", "Pet play", "Collar", "Bondage", "Rope",
            "Spanking", "Choking", "Hair pulling", "Biting", "Marking", "Rough", "Primal", "Edging",
            "Orgasm control", "Overstimulation", "Breeding", "Size difference", "Dirty talk",
            "Possessive sex", "Public", "Exhibitionism", "Voyeurism", "Sensory deprivation",
            "Blindfold", "Restraints", "Brat taming", "Worship", "Aftercare",
        )),
        Category("Other", emptyList()),
    )

    val categoryNames: List<String> get() = categories.map { it.name }

    fun make(category: String, name: String) = "$category$SEP${name.trim()}"
    fun category(tag: String): String = if (SEP in tag) tag.substringBefore(SEP) else "Other"
    fun name(tag: String): String = tag.substringAfter(SEP)

    /** Turns the old comma-separated tag text into tags in "Other". */
    fun fromLegacy(text: String): List<String> =
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { make("Other", it) }

    fun grouped(tags: List<String>): List<Pair<String, List<String>>> =
        tags.groupBy { category(it) }.toList().sortedBy { (c, _) -> categoryNames.indexOf(c).let { if (it < 0) 99 else it } }

    /** Plain text for prompts, e.g. "Personality: Dominant, Brooding; Setting: Church". */
    fun describe(tags: List<String>): String =
        grouped(tags).joinToString("; ") { (c, list) -> "$c: " + list.joinToString(", ") { name(it) } }
}
