# ENTITÉS JPA CRÉÉES

## Structure des packages

```
com.stocksaas.model/
├── BaseEntity.java              # Classe de base avec id, createdAt, updatedAt, isDeleted
├── td/                          # Tables dynamiques (données métier)
│   ├── Company.java            # td_companies
│   ├── User.java               # td_users
│   ├── Warehouse.java          # td_warehouses
│   ├── Product.java            # td_products
│   ├── StockLevel.java         # td_stock_levels
│   ├── Movement.java           # td_movements
│   └── AuditLog.java           # td_audit_logs
├── tp/                          # Tables de référence (énumérations)
│   ├── SubscriptionPlan.java   # tp_subscription_plan
│   ├── UserRole.java           # tp_user_role
│   ├── CompanyStatus.java      # tp_company_status
│   ├── WarehouseStatus.java    # tp_warehouse_status
│   ├── ProductStatus.java      # tp_product_status
│   └── MovementType.java       # tp_movement_type
└── tr/                          # Tables de relation
    ├── UserWarehouse.java      # tr_user_warehouses
    └── UserWarehouseId.java    # Clé composite pour UserWarehouse
```

## Convention de nommage

### Tables dynamiques (TD)
- Préfixe : `td_`
- Exemple : `@Table(name = "td_companies")`
- Contiennent les données métier principales

### Tables de référence (TP)
- Préfixe : `tp_`
- Exemple : `@Table(name = "tp_user_role")`
- Contiennent les énumérations et données de référence

### Tables de relation (TR)
- Préfixe : `tr_`
- Exemple : `@Table(name = "tr_user_warehouses")`
- Tables de liaison N-N

## Entités créées

### 1. BaseEntity (Classe abstraite)
- Champs communs : `id`, `createdAt`, `updatedAt`, `isDeleted`
- Utilisée par toutes les entités TD

### 2. Tables dynamiques (TD)

#### Company (`td_companies`)
- Relations : users, warehouses, products, movements
- Références TP : plan (SubscriptionPlan), status (CompanyStatus)

#### User (`td_users`)
- Relations : company, assignedWarehouses, movements
- Références TP : role (UserRole)
- Méthodes helper : `isSuperAdmin()`, `isAdminEntreprise()`, etc.

#### Warehouse (`td_warehouses`)
- Relations : company, assignedUsers, products, stockLevels, movements
- Références TP : status (WarehouseStatus)

#### Product (`td_products`)
- Relations : company, warehouse, stockLevels, movements
- Références TP : status (ProductStatus)
- Contrainte unique : (company_id, sku)

#### StockLevel (`td_stock_levels`)
- Relations : product, warehouse
- Contrainte unique : (product_id, warehouse_id)

#### Movement (`td_movements`)
- Relations : company, product, warehouse, destinationWarehouse, user
- Références TP : type (MovementType)

#### AuditLog (`td_audit_logs`)
- Relations : user, company
- Champ JSONB pour les détails

### 3. Tables de référence (TP)

#### SubscriptionPlan (`tp_subscription_plan`)
- Code : Free, Basique, Standard, Premium
- Champs : price, maxUsers, maxWarehouses, trialDays

#### UserRole (`tp_user_role`)
- Code : SUPER_ADMIN, ADMIN_ENTREPRISE, GESTIONNAIRE, UTILISATEUR

#### CompanyStatus (`tp_company_status`)
- Code : Actif, Inactif, Suspendu

#### WarehouseStatus (`tp_warehouse_status`)
- Code : Actif, Inactif, Maintenance

#### ProductStatus (`tp_product_status`)
- Code : En stock, Rupture

#### MovementType (`tp_movement_type`)
- Code : Entrée, Sortie, Transfert, Ajustement
- Champs : allowsNegative, requiresDestination

### 4. Tables de relation (TR)

#### UserWarehouse (`tr_user_warehouses`)
- Clé composite : (user_id, warehouse_id)
- Classe d'ID : UserWarehouseId

## Caractéristiques techniques

### Annotations utilisées
- `@Entity` : Définit l'entité JPA
- `@Table(name = "...")` : Nom de la table avec préfixe
- `@Id` : Clé primaire
- `@GeneratedValue` : Génération automatique
- `@ManyToOne`, `@OneToMany` : Relations JPA
- `@JoinColumn` : Colonnes de jointure
- `@Index` : Index de performance
- `@UniqueConstraint` : Contraintes d'unicité
- `@Column` : Configuration des colonnes
- `@CreationTimestamp`, `@UpdateTimestamp` : Timestamps automatiques

### Lombok
- `@Data` : Getters, setters, equals, hashCode, toString
- `@NoArgsConstructor`, `@AllArgsConstructor` : Constructeurs
- `@EqualsAndHashCode(callSuper = true)` : Pour héritage
- `@ToString(exclude = "...")` : Exclusion de relations pour éviter les boucles

### Validation
- `@NotBlank`, `@NotNull` : Validation des champs
- `@Min(0)` : Validation des valeurs numériques
- `@Email` : Validation des emails

### Types de données
- `BigDecimal` : Pour les montants et quantités (précision)
- `LocalDate` : Pour les dates
- `LocalDateTime` : Pour les timestamps
- `Map<String, Object>` : Pour JSONB (audit_logs)

## Prochaines étapes

1. Créer les Repositories JPA dans `repository/`
2. Créer les Services dans `service/`
3. Créer les DTOs dans `dto/`
4. Créer les Controllers REST dans `controller/`
5. Créer les scripts de données initiales pour les tables TP
