# Stock SaaS Backend

Backend API pour l'application Stock SaaS développée avec Spring Boot 3 et Java 21.

## Prérequis

- Java 21
- Maven 3.6+
- PostgreSQL 12+

## Configuration

1. Créer la base de données PostgreSQL :
```sql
CREATE DATABASE stock_saas_db;
```

2. Configurer les paramètres de connexion dans `src/main/resources/application.properties`

3. Lancer l'application :
```bash
mvn spring-boot:run
```

## Structure du projet

```
src/
├── main/
│   ├── java/com/stocksaas/
│   │   ├── controller/     # Contrôleurs REST
│   │   ├── service/         # Services métier
│   │   ├── repository/      # Repositories JPA
│   │   ├── model/           # Entités JPA
│   │   ├── dto/             # Data Transfer Objects
│   │   ├── config/          # Configuration Spring
│   │   ├── security/        # Configuration sécurité
│   │   └── exception/       # Gestion des exceptions
│   └── resources/
│       ├── db/migration/    # Scripts de migration
│       └── application.properties
└── test/
    └── java/com/stocksaas/  # Tests unitaires et d'intégration
```

## Profils

- `dev` : Configuration de développement
- `prod` : Configuration de production

Pour utiliser un profil :
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
