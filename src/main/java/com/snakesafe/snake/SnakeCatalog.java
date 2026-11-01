package com.snakesafe.snake;

import java.util.List;

/**
 * Reference list of snakes found in Karnataka: scientific names and venom status follow standard herpetology
 * references. Venomous = has venom that can harm people, including mildly venomous rear-fanged snakes (their
 * descriptions say so). Districts are broad range groups, not exact maps. Kannada names are common usage or simple
 * transliterations and should be reviewed by a native speaker. Texts give no treatment advice.
 */
public final class SnakeCatalog {

    public record Entry(String nameEn, String nameKn, String scientificName, boolean venomous, String description,
            String habitat, String districts) {
    }

    static final String STATEWIDE = "Statewide";
    static final String GHATS = "Uttara Kannada, Shivamogga, Chikkamagaluru, Kodagu, Hassan, Udupi, Dakshina Kannada";
    static final String COAST = "Uttara Kannada, Udupi, Dakshina Kannada";
    static final String DRY = "Kalaburagi, Vijayapura, Bagalkot, Raichur, Ballari, Koppal, Gadag, Yadgir, Bidar, Chitradurga, Tumakuru";
    static final String SOUTH = "Mysuru, Chamarajanagar, Mandya, Ramanagara, Bengaluru Urban, Bengaluru Rural, Kolar, Chikkaballapur";
    private static final String MILD = " Mildly venomous (rear-fanged): bites are rarely serious, but treat every bite as an emergency.";

    private SnakeCatalog() {
    }

    public static final List<Entry> ALL = List.of(
            new Entry("Indian Cobra", "ನಾಗರಹಾವು", "Naja naja", true,
                    "A medium to large snake that spreads a hood when alarmed. Brown, grey or black, sometimes with a spectacle mark on the hood. One of the \"big four\" snakes behind most serious bites in India.",
                    "Fields, villages, farmland, rocky areas and forest edges. Often near rat holes and water.", STATEWIDE),
            new Entry("King Cobra", "ಕಾಳಿಂಗ ಸರ್ಪ", "Ophiophagus hannah", true,
                    "The longest venomous snake in the world, often 3 to 4 metres. Olive, brown or black with pale bands and a tall hood. Feeds mostly on other snakes and avoids people.",
                    "Dense evergreen and moist forests of the Western Ghats.", GHATS),
            new Entry("Common Krait", "ಕಟ್ಟು ಹಾವು", "Bungarus caeruleus", true,
                    "A slim, shiny black or blue-black snake with thin white bands. Active at night and often found indoors in villages. A bite can hurt very little, so never wait for symptoms. One of the \"big four\".",
                    "Farmland, village homes, dry fields and near water. Rests in dark corners during the day.", STATEWIDE),
            new Entry("Russell's Viper", "ಕೊಳಕು ಮಂಡಲ", "Daboia russelii", true,
                    "A heavy-bodied snake with a pattern of large dark rings along a brown back. Hisses loudly when disturbed. Often bites people working in crop fields. One of the \"big four\".",
                    "Open grassland, farms, scrub and rocky ground.", STATEWIDE),
            new Entry("Saw-scaled Viper", "ಗರಗಸ ಮಂಡಲ", "Echis carinatus", true,
                    "A small, thin snake, sandy or brown with a pale zig-zag line. Rubs its scales together with a rasping sound when alarmed. One of the \"big four\".",
                    "Dry, sandy and rocky country. Hides under stones and in loose soil.", DRY),
            new Entry("Malabar Pit Viper", "ಮಲಬಾರ್ ಪಿಟ್ ವೈಪರ್", "Trimeresurus malabaricus", true,
                    "A small green, yellow or brown viper with a triangular head. Often rests on low branches and bushes, so bites usually happen on the hands and arms.",
                    "Forests, coffee and tea plantations and bushes in the Western Ghats.", GHATS),
            new Entry("Hump-nosed Pit Viper", "ಹಂಪ್-ನೋಸ್ಡ್ ಪಿಟ್ ವೈಪರ್", "Hypnale hypnale", true,
                    "A small, brown, rough-scaled viper with an upturned snout. Easily mistaken for a harmless snake or a dead leaf.",
                    "Leaf litter, plantations and forest floor in the Western Ghats and the coast.", GHATS),
            new Entry("Slender Coral Snake", "ಹವಳದ ಹಾವು", "Calliophis melanurus", true,
                    "A small, shy snake with a glossy body, a red or orange underside and black bands near the tail. Bites are rare.",
                    "Leaf litter, loose soil and under stones; forest edges and gardens.", GHATS + ", " + SOUTH),
            new Entry("Striped Coral Snake", "ಪಟ್ಟೆ ಹವಳದ ಹಾವು", "Calliophis nigrescens", true,
                    "A glossy black or dark brown snake with bright red or orange stripes along the body and a red head. Rarely seen.",
                    "Moist forests and plantations of the Western Ghats.", GHATS),
            new Entry("Beaked Sea Snake", "ಸಮುದ್ರ ಹಾವು", "Hydrophis schistosus", true,
                    "A thick-bodied sea snake with a flat, oar-like tail. Sometimes caught in fishing nets. Do not handle it, even if it looks dead.",
                    "Coastal waters, estuaries and river mouths.", COAST),
            new Entry("Indian Rat Snake", "ಕೇರೆ ಹಾವು", "Ptyas mucosa", false,
                    "A long, fast snake, pale brown to black, with dark markings on the back half. Often mistaken for a cobra. Not venomous, but it bites hard when cornered.",
                    "Farms, villages, gardens, trees and rocky places.", STATEWIDE),
            new Entry("Green Vine Snake", "ಹಸಿರು ಹಾವು", "Ahaetulla nasuta", true,
                    "A very thin, bright green snake with a long pointed head. Hard to see in leaves." + MILD,
                    "Bushes, hedges, gardens and forest edges.", STATEWIDE),
            new Entry("Brown Vine Snake", "ಕಂದು ಬಳ್ಳಿ ಹಾವು", "Ahaetulla pulverulenta", true,
                    "A very thin, grey-brown or tan tree snake with a long pointed head." + MILD,
                    "Bushes, hedges and trees in forests and gardens.", GHATS + ", " + SOUTH),
            new Entry("Common Bronzeback", "ಕಂಚಿನ ಬೆನ್ನಿನ ಹಾವು", "Dendrelaphis tristis", false,
                    "A slim, fast tree snake, bronze-brown above with a pale yellow stripe along the side. Harmless to people.",
                    "Trees, bushes and gardens, often near water.", STATEWIDE),
            new Entry("Common Trinket Snake", "ಟ್ರಿಂಕೆಟ್ ಹಾವು", "Coelognathus helena", false,
                    "A fast, strong-looking snake with black stripes on a brown body and dark marks on the head. Not venomous, but may bite when cornered.",
                    "Farms, gardens, old walls and rocky places.", STATEWIDE),
            new Entry("Common Cat Snake", "ಬೆಕ್ಕು ಕಣ್ಣಿನ ಹಾವು", "Boiga trigonata", true,
                    "A slim, grey-brown snake with a large head and cat-like eyes." + MILD,
                    "Dry scrub, farms and village outskirts.", DRY),
            new Entry("Forsten's Cat Snake", "ಫೋರ್ಸ್ಟನ್ ಬೆಕ್ಕು ಕಣ್ಣಿನ ಹಾವು", "Boiga forsteni", true,
                    "A long, thin snake with large eyes and dark bands, active at night, often in trees." + MILD,
                    "Forests, plantations and village homes, often in trees.", GHATS + ", " + SOUTH),
            new Entry("Common Wolf Snake", "ವುಲ್ಫ್ ಹಾವು", "Lycodon aulicus", false,
                    "A small, thin brown snake with pale bands that fade with age. Often found in houses at night, and looks like a krait. Not venomous, but a bite should still be treated as an emergency until a doctor says otherwise.",
                    "Houses, outbuildings, gardens, stone walls and rubbish heaps.", STATEWIDE),
            new Entry("Barred Wolf Snake", "ಪಟ್ಟೆ ವುಲ್ಫ್ ಹಾವು", "Lycodon striatus", false,
                    "A small, glossy dark snake with thin pale bars. Found around old walls and stones. Sometimes mistaken for a krait.",
                    "Rocky ground, old walls, dry forest and gardens.", STATEWIDE),
            new Entry("Banded Kukri Snake", "ಕುಕ್ರಿ ಹಾವು", "Oligodon arnensis", false,
                    "A small snake with a pale body, dark bands and a V-shaped mark on the head. Not dangerous to people.",
                    "Farms, gardens and dry open ground.", STATEWIDE),
            new Entry("Banded Racer", "ಪಟ್ಟೆ ವೇಗದ ಹಾವು", "Argyrogena fasciolata", false,
                    "A fast, slim snake with dark bands that fade with age. Not venomous.",
                    "Dry open country, farms and rocky ground.", DRY),
            new Entry("Ornate Flying Snake", "ಹಾರುವ ಹಾವು", "Chrysopelea ornata", true,
                    "A slim, brightly marked tree snake that glides between trees by flattening its body. Not dangerous to people." + MILD,
                    "Forests and tree-filled areas of the Western Ghats.", GHATS),
            new Entry("Checkered Keelback", "ನೀರು ಹಾವು", "Fowlea piscator", false,
                    "A common water snake with a checkered olive and dark pattern. Often seen in ponds and paddy fields. Not dangerous to people.",
                    "Ponds, lakes, streams, paddy fields and wet ground.", STATEWIDE),
            new Entry("Buff-striped Keelback", "ಪಟ್ಟೆ ನೀರು ಹಾವು", "Amphiesma stolatum", false,
                    "A small snake with two pale stripes along the back, seen near water and in grass. Not dangerous to people.",
                    "Grassland, paddy fields, gardens and streams.", STATEWIDE),
            new Entry("Olive Keelback", "ಹಸಿರು ನೀರು ಹಾವು", "Atretium schistosum", false,
                    "An olive-brown water snake found in ponds, paddy fields and streams. Not dangerous to people.",
                    "Ponds, streams, paddy fields and wet ground.", STATEWIDE),
            new Entry("Indian Rock Python", "ಹೆಬ್ಬಾವು", "Python molurus", false,
                    "A very large, heavy, non-venomous snake with brown blotches on a pale body. Squeezes its prey, and a large one can hurt a person.",
                    "Forests, grassland near water, rocky hills and farmland edges.", GHATS + ", Mysuru, Chamarajanagar"),
            new Entry("Red Sand Boa", "ಮಣ್ಣುಮುಕ್ಕ ಹಾವು", "Eryx johnii", false,
                    "A short, thick snake with a blunt tail that looks like its head. Brown with darker patches. Spends most of its time under loose soil. Not venomous.",
                    "Dry, sandy and rocky ground and farmland.", DRY),
            new Entry("Rough-scaled Sand Boa", "ಒರಟು ಮಣ್ಣುಮುಕ್ಕ ಹಾವು", "Gongylophis conicus", false,
                    "A short, stout snake with rough scales and a blunt tail. Spends most of its time under loose soil. Not venomous.",
                    "Dry, sandy and rocky ground and farmland.", DRY),
            new Entry("Brahminy Blind Snake", "ಕುರುಡು ಹಾವು", "Indotyphlops braminus", false,
                    "A tiny, shiny, worm-like snake only 10 to 15 cm long, often found in flowerpots and damp soil. Harmless.",
                    "Damp soil, gardens, flowerpots and under stones.", STATEWIDE),
            new Entry("Dumeril's Black-headed Snake", "ಕಪ್ಪು ತಲೆಯ ಹಾವು", "Sibynophis subpunctatus", false,
                    "A small, slim snake with a dark head and a pale collar. Not dangerous to people.",
                    "Leaf litter, rocky ground and gardens.", STATEWIDE));
}
