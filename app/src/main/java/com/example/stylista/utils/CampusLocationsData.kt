package com.example.stylista.utils

import com.example.stylista.database.entities.CampusLocationEntity
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.example.stylista.R
import com.example.stylista.activities.*
import com.example.stylista.adapters.*
import com.example.stylista.chatbot.*
import com.example.stylista.database.StylistaDatabase
import com.example.stylista.database.dao.*
import com.example.stylista.database.entities.*
import com.example.stylista.models.*
import com.example.stylista.repositories.*

/**
 * Configuration for Stylista Boutiques, Campus Stores, and Fashion Pickup Hubs.
 */
object CampusLocationsData {

    const val CAMPUS_NAME = "Stylista Boutiques & Campus Fashion Hubs"
    val CAMPUS_CENTER = LatLng(19.075714, 72.991710)
    const val DEFAULT_ZOOM = 18.2f

    // Bounding box enclosing the primary boutique district / campus area
    val CAMPUS_BOUNDS: LatLngBounds = LatLngBounds(
        LatLng(19.071500, 72.987500), // Southwest corner
        LatLng(19.079500, 72.996000)  // Northeast corner
    )

    /**
     * Stylista Boutiques, Fitting Studios, and Fashion Pickup Hubs.
     */
    val defaultLocations: List<CampusLocationEntity> = listOf(
        CampusLocationEntity(
            id = 1,
            name = "Stylista Flagship Boutique & Styling Lounge",
            category = "Boutique",
            description = "Central fashion showroom featuring Western and Formal collections, private styling suites, and seasonal lookbook trials.",
            latitude = 19.075714,
            longitude = 72.991710,
            floor = "Ground & 1st Floor",
            openingHours = "10:00 AM - 9:30 PM"
        ),
        CampusLocationEntity(
            id = 2,
            name = "Stylista Campus Fashion Store",
            category = "Campus Store",
            description = "Dedicated campus student boutique offering exclusive student discounts, casual wear, and express order pick-up counter.",
            latitude = 19.075950,
            longitude = 72.991850,
            floor = "Academic Wing, 2nd Level",
            openingHours = "8:30 AM - 7:30 PM"
        ),
        CampusLocationEntity(
            id = 3,
            name = "Stylista Atelier & Custom Tailoring Bar",
            category = "Atelier",
            description = "Bespoke tailoring, luxury silk anarkali fittings, saree draping consultation, and quick alterations bar.",
            latitude = 19.076080,
            longitude = 72.991320,
            floor = "Design Wing, Suite 102",
            openingHours = "10:30 AM - 8:00 PM"
        ),
        CampusLocationEntity(
            id = 4,
            name = "Stylista Express Pickup & Returns Station",
            category = "Pickup Hub",
            description = "Contactless automated lockers and express pickup counter for orders placed in the Stylista shopping bag.",
            latitude = 19.075420,
            longitude = 72.991400,
            floor = "Main Gate Entrance Plaza",
            openingHours = "7:00 AM - 10:00 PM"
        ),
        CampusLocationEntity(
            id = 5,
            name = "Stylista Accessories & Fine Jewelry Bar",
            category = "Accessories Hub",
            description = "Curated gallery of leather bags, gold-plated jewelry, designer watches, and UV sunglasses with try-on mirrors.",
            latitude = 19.075600,
            longitude = 72.992100,
            floor = "3rd Floor Promenade",
            openingHours = "9:30 AM - 8:30 PM"
        ),
        CampusLocationEntity(
            id = 6,
            name = "Stylista Sports & Athleisure Studio",
            category = "Athleisure",
            description = "Activewear performance fitting zone with breathable gym leggings, sports sets, and comfort sneakers.",
            latitude = 19.075250,
            longitude = 72.991900,
            floor = "Ground Floor Sports Arena",
            openingHours = "7:00 AM - 9:00 PM"
        ),
        CampusLocationEntity(
            id = 7,
            name = "Stylista Fashion Runway & Event Pavilion",
            category = "Boutique",
            description = "Venue for seasonal fashion showcases, student design exhibitions, and new collection launch parties.",
            latitude = 19.075800,
            longitude = 72.992300,
            floor = "Auditorium Promenade",
            openingHours = "9:00 AM - 8:00 PM"
        )
    )
}
