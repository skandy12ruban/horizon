package tv.steroid.home;

/**
 * Retro TV's own channels. Cartoons, music, TV shows, movies and comedy come from the MyRetroTVs
 * decade sites; Science (which they lack) uses these YouTube playlists. A channel with several
 * playlists switches to another after each programme. Format: {channel name, playlist id, ...}.
 */
final class Lineup {
    static final String[] CATEGORIES = {"Cartoons", "Music", "TV shows", "Movies", "Comedy", "Science"};

    /** Science channels per decade (the decade sites have no science category). */
    static final String[][][] SCIENCE = {
        { // 1980s
            {"Curiosity Hour", "PLB5CA6AF3C067115B", "PL8D1D0E1DB8598123", "PL2n4in4bgfuiHxBvCYUDQQaZoGmXNaH2C"},
            {"Wild Planet", "PLHMuMcHkEcsrjOfgmj3LSvc6YDP-I3o3N"},
            {"Attenborough", "PLz58QJ68R9CTFoR1J6dgAc4mwdAmm3kZZ"},
            {"School TV", "PL5ZqJI2-Z0-oD2yNdHiuAWBx3h5coXSVA"},
        },
        { // 1990s
            {"Big Kids' Science", "PL8LlGQosRwWInqLNMHo7rcWSU0ajf7Aog"},
            {"Eyewitness", "PLusDmLPGRMU_yAZIwLc5GsImV0oRaASsh"},
            {"Nature", "PLpPocMZqzNMECQeFMxTj9BToRbusKf7PZ", "PLz58QJ68R9CSw5YUr1oAjD9dXDj8qbkRw"},
            {"Wild Africa", "PLdi0yWC0-0AaKddDLN_GJIEHnX-G09Gf_"},
        },
        { // 2000s
            {"Myths & Experiments", "PL7tc-XM_jjo0sULRnHhK6SL1PDjjpYslh", "PL7tc-XM_jjo1CmQeS_bChp58rILXRNlIW",
                "PL7tc-XM_jjo237MtDN2yUVCMWan07NZ1c", "PL7tc-XM_jjo0uHTrivL5BzRVFVvqA_G7J", "PLWEucAYhy7NPwWWlLfRCO0e9lK2CHVf--"},
            {"Planet Earth", "PLYbocufkwRFBJf9D_b4nQj4h1zxAva_13", "PLz58QJ68R9CSpIPd_cjteMrVChTY3y2MR"},
            {"Space & Geology", "PLz58QJ68R9CSuBePNqa16GtYjQmJ_Pdn5"},
            {"Eyewitness", "PL5JAdAtlGVJ40Jwk4sMNqCVu_tqgvUiwx"},
        },
        { // 2010s
            {"National Geographic", "UUpVm7bg6pXKo1Pr6k5kxG9A"},
            {"Kurzgesagt", "UUsXVk37bltHxD1rDPwtNM8Q"},
            {"Veritasium", "UUHnyfMqiRRG1u-2MsSQLbXA"},
            {"Vsauce", "UU6nSFpj9HTCZ5t-N3Rm3-HA"},
            {"Universe", "PL39_ud5aKSvkl_wj5BIkRvANCiiUPL3VS", "PL338F3E1F4620A89E"},
        },
    };

    /** Only Science has its own channels; every other category is served by the decade sites. */
    static String[][] channels(int decade, int category) {
        return category == CATEGORIES.length - 1 ? SCIENCE[decade] : new String[][]{{"Channel surfing"}};
    }
}
