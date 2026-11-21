package com.ejemplo.smartbus.data

import com.ejemplo.smartbus.R
import com.ejemplo.smartbus.data.model.Reward

/**
 * Catálogo de recompensas disponibles con fotos reales y datos oficiales.
 */
object RewardsCatalog {

    val rewards: List<Reward> = listOf(
        Reward(
            id = 1,
            name = "Café Latte",
            description = "Café espresso cremoso con leche vaporizada en cafetería asociada MistiGo",
            points = 40,
            stock = 50,
            category = "cafeteria",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1534778101976-62847782c213?auto=format&fit=crop&w=600&q=80"
        ),
        Reward(
            id = 2,
            name = "Empanada",
            description = "Empanada artesanal recién horneada en locales asociados MistiGo",
            points = 60,
            stock = 40,
            category = "cafeteria",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1608897013039-887f21d8c804?auto=format&fit=crop&w=600&q=80",
            imageResId = R.drawable.reward_empanada
        ),
        Reward(
            id = 3,
            name = "Pasaje Gratis",
            description = "Vale por 1 pasaje urbano totalmente gratis en cualquier ruta de MistiGo",
            points = 20,
            stock = 100,
            category = "pasajes",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1544620347-c4fd4a3d5957?auto=format&fit=crop&w=600&q=80"
        ),
        Reward(
            id = 4,
            name = "Descuento 30%",
            description = "Cupón de 30% de descuento directo en tu pasaje o recarga MistiGo",
            points = 30,
            stock = 80,
            category = "beneficios",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1559526324-4b87b5e36e44?auto=format&fit=crop&w=600&q=80"
        ),
        Reward(
            id = 5,
            name = "Jugo Natural",
            description = "Jugo de fruta natural recién preparado en cafeterías y juguerías asociadas",
            points = 50,
            stock = 40,
            category = "cafeteria",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1613478223719-2ab802602423?auto=format&fit=crop&w=600&q=80"
        ),
        Reward(
            id = 6,
            name = "Café Americano",
            description = "Café espresso seleccionado con notas de cacao y agua purificada",
            points = 40,
            stock = 50,
            category = "cafeteria",
            isAvailable = true,
            imageUrl = "https://images.unsplash.com/photo-1514432324607-a09d9b4aefdd?auto=format&fit=crop&w=600&q=80"
        )
    )

    fun byId(id: Int): Reward? = rewards.firstOrNull { it.id == id }
}
