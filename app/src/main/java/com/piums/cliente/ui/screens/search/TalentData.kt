package com.piums.cliente.ui.screens.search

data class Talent(val id: String, val label: String, val category: String)
data class TalentSubCategory(val id: String, val label: String, val talents: List<Talent>)
data class TalentGroup(val id: String, val label: String, val subCategories: List<TalentSubCategory>)

val TALENT_GROUPS = listOf(
    TalentGroup("musica_audio", "Música & Audio", listOf(
        TalentSubCategory("musico", "Músico", listOf(
            Talent("cantante_solista",  "Cantante Solista",    "MUSICO"),
            Talent("banda_musical",     "Banda Musical",       "MUSICO"),
            Talent("mariachi",          "Mariachi",            "MUSICO"),
            Talent("grupo_acustico",    "Grupo Acústico",      "MUSICO"),
            Talent("trio_cuarteto",     "Trío / Cuarteto",     "MUSICO"),
            Talent("pianista",          "Pianista",            "MUSICO"),
            Talent("guitarrista",       "Guitarrista",         "MUSICO"),
            Talent("violinista",        "Violinista",          "MUSICO"),
            Talent("baterista",         "Baterista",           "MUSICO"),
            Talent("saxofonista",       "Saxofonista",         "MUSICO"),
            Talent("marimba",           "Marimba",             "MUSICO"),
        )),
        TalentSubCategory("produccion_audio", "Producción & Audio", listOf(
            Talent("productor_musical", "Productor Musical",    "MUSICO"),
            Talent("beatmaker",         "Beatmaker",            "DJ"),
            Talent("rapero_freestyle",  "Rapero / Freestyle",   "MUSICO"),
            Talent("ingeniero_sonido",  "Ingeniero de Sonido",  "MUSICO"),
            Talent("locutor_voiceover", "Locutor / Voice Over", "MUSICO"),
        )),
        TalentSubCategory("dj", "DJ", listOf(
            Talent("dj_bodas",       "DJ para Bodas",  "DJ"),
            Talent("dj_corporativo", "DJ Corporativo", "DJ"),
            Talent("dj_electronica", "DJ Electrónica", "DJ"),
            Talent("dj_generalista", "DJ Eventos",     "DJ"),
        )),
    )),
    TalentGroup("audiovisual", "Producción Audiovisual", listOf(
        TalentSubCategory("fotografia", "Fotografía", listOf(
            Talent("fotografo_eventos",  "Fotógrafo de Eventos",  "FOTOGRAFO"),
            Talent("fotografo_retrato",  "Fotógrafo de Retrato",  "FOTOGRAFO"),
            Talent("fotografo_producto", "Fotógrafo de Producto", "FOTOGRAFO"),
            Talent("fotografo_boda",     "Fotógrafo de Bodas",    "FOTOGRAFO"),
        )),
        TalentSubCategory("video", "Video", listOf(
            Talent("videografo",           "Videógrafo",           "VIDEOGRAFO"),
            Talent("editor_video",         "Editor de Video",      "VIDEOGRAFO"),
            Talent("director_audiovisual", "Director Audiovisual", "VIDEOGRAFO"),
            Talent("drone_operator",       "Drone Operator",       "VIDEOGRAFO"),
            Talent("streaming",            "Streaming / En Vivo",  "VIDEOGRAFO"),
        )),
    )),
    TalentGroup("diseno_arte", "Diseño & Arte Visual", listOf(
        TalentSubCategory("diseno_grafico", "Diseño Gráfico", listOf(
            Talent("disenador_grafico", "Diseñador Gráfico",    "DISENADOR"),
            Talent("disenador_uxui",    "Diseñador UX/UI",      "DISENADOR"),
            Talent("branding",          "Branding / Identidad", "DISENADOR"),
            Talent("ilustrador",        "Ilustrador",           "PINTOR"),
            Talent("motion_graphics",   "Motion Graphics",      "DISENADOR"),
        )),
        TalentSubCategory("arte_fisico", "Arte Físico", listOf(
            Talent("pintor",    "Pintor / Artista", "PINTOR"),
            Talent("escultor",  "Escultor",         "ESCULTOR"),
            Talent("caligrafo", "Calígrafo",        "PINTOR"),
            Talent("artesano",  "Artesano",         "PINTOR"),
        )),
    )),
    TalentGroup("artes_escenicas", "Artes Escénicas", listOf(
        TalentSubCategory("danza", "Danza", listOf(
            Talent("bailarin_urbano",  "Bailarín Urbano",  "BAILARIN"),
            Talent("bailarin_clasico", "Bailarín Clásico", "BAILARIN"),
            Talent("coreografo",       "Coreógrafo",       "BAILARIN"),
            Talent("danza_folklorica", "Danza Folklórica", "BAILARIN"),
        )),
        TalentSubCategory("actuacion", "Actuación", listOf(
            Talent("actor_actriz", "Actor / Actriz",     "ANIMADOR"),
            Talent("teatro",       "Teatro",             "ANIMADOR"),
            Talent("mimo",         "Mimo / Performance", "ANIMADOR"),
        )),
    )),
    TalentGroup("eventos_entretenimiento", "Eventos & Entretenimiento", listOf(
        TalentSubCategory("hosting", "Hosting & Animación", listOf(
            Talent("animador_mc",      "Animador / MC",     "ANIMADOR"),
            Talent("host_eventos",     "Host de Eventos",   "ANIMADOR"),
            Talent("comedian_standup", "Stand-up Comedian", "ANIMADOR"),
            Talent("show_infantil",    "Shows Infantiles",  "ANIMADOR"),
        )),
        TalentSubCategory("shows_especiales", "Shows Especiales", listOf(
            Talent("mago_ilusionista",  "Mago / Ilusionista",   "MAGO"),
            Talent("acrobata",          "Acróbata",             "ACROBATA"),
            Talent("show_fuego",        "Show de Fuego",        "ACROBATA"),
            Talent("animacion_fiestas", "Animación de Fiestas", "ANIMADOR"),
        )),
    )),
    TalentGroup("cultura_tradicion", "Cultura & Tradición", listOf(
        TalentSubCategory("musica_tradicional", "Música Tradicional", listOf(
            Talent("marimba_orquesta",     "Marimba Orquesta",     "MUSICO"),
            Talent("mariachi_tradicional", "Mariachi Tradicional", "MUSICO"),
            Talent("musico_regional",      "Músico Regional",      "MUSICO"),
        )),
        TalentSubCategory("danza_cultural", "Danza Cultural", listOf(
            Talent("danza_folklorica_trad", "Danza Folklórica", "BAILARIN"),
            Talent("danza_indigena",        "Danza Indígena",   "BAILARIN"),
        )),
    )),
    TalentGroup("educacion_creativa", "Educación Creativa", listOf(
        TalentSubCategory("docencia_artistica", "Docencia Artística", listOf(
            Talent("profesor_musica", "Profesor de Música", "MUSICO"),
            Talent("clases_canto",    "Clases de Canto",    "MUSICO"),
            Talent("clases_pintura",  "Clases de Pintura",  "PINTOR"),
            Talent("taller_creativo", "Talleres Creativos", "OTRO"),
            Talent("coaching_vocal",  "Coaching Vocal",     "MUSICO"),
        )),
    )),
    TalentGroup("contenido_digital", "Contenido Digital", listOf(
        TalentSubCategory("escritura", "Escritura & Guiones", listOf(
            Talent("escritor",    "Escritor",      "ESCRITOR"),
            Talent("guionista",   "Guionista",     "ESCRITOR"),
            Talent("letrista",    "Letrista",      "ESCRITOR"),
            Talent("copywriter",  "Copy Creativo", "ESCRITOR"),
            Talent("storyteller", "Storyteller",   "ESCRITOR"),
        )),
        TalentSubCategory("social_media", "Social Media", listOf(
            Talent("creador_contenido", "Creador de Contenido", "CREADOR_CONTENIDO"),
            Talent("tiktoker",          "TikToker / Reels",     "CREADOR_CONTENIDO"),
            Talent("youtuber",          "YouTuber",             "VIDEOGRAFO"),
        )),
    )),
    TalentGroup("belleza_estilo", "Belleza & Estilo", listOf(
        TalentSubCategory("maquillaje", "Maquillaje & Beauty", listOf(
            Talent("maquillador_eventos", "Maquillador/a Eventos",  "MAQUILLADOR"),
            Talent("maquillaje_novia",    "Especialista en Novias", "MAQUILLADOR"),
            Talent("body_paint",          "Body Paint Artist",      "MAQUILLADOR"),
            Talent("estilista",           "Estilista",              "MAQUILLADOR"),
            Talent("barbero_pro",         "Barbero Profesional",    "MAQUILLADOR"),
        )),
        TalentSubCategory("tatuaje", "Tatuaje & Body Art", listOf(
            Talent("tatuador",           "Tatuador",           "TATUADOR"),
            Talent("tattoo_realista",    "Tattoo Realista",    "TATUADOR"),
            Talent("tattoo_minimalista", "Tattoo Minimalista", "TATUADOR"),
            Talent("piercing_artist",    "Piercing Artist",    "TATUADOR"),
        )),
    )),
    TalentGroup("experiencias_creativas", "Experiencias Creativas", listOf(
        TalentSubCategory("eventos_especiales", "Eventos Especiales", listOf(
            Talent("chef_creativo",     "Chef Creativo",        "OTRO"),
            Talent("bartender_show",    "Bartender Show",       "OTRO"),
            Talent("decorador_eventos", "Decorador de Eventos", "OTRO"),
            Talent("wedding_planner",   "Wedding Planner",      "OTRO"),
            Talent("banda_boda",        "Banda para Bodas",     "MUSICO"),
        )),
    )),
)
