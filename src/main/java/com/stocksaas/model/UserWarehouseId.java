package com.stocksaas.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;
import java.util.Objects;

/**
 * Clé composite pour la table tr_user_warehouses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserWarehouseId implements Serializable {
    
    private Long user;
    private Long warehouse;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UserWarehouseId that = (UserWarehouseId) o;
        return Objects.equals(user, that.user) && Objects.equals(warehouse, that.warehouse);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(user, warehouse);
    }
}
