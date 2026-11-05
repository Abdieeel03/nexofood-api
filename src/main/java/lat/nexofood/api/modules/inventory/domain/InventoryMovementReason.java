package lat.nexofood.api.modules.inventory.domain;

public enum InventoryMovementReason {
    // Entradas
    PURCHASE(InventoryMovementType.ENTRY),
    INITIAL_STOCK(InventoryMovementType.ENTRY),
    CUSTOMER_RETURN(InventoryMovementType.ENTRY),
    TRANSFER_IN(InventoryMovementType.ENTRY),

    // Salidas y Mermas
    KITCHEN_CONSUMPTION_OR_WASTE(InventoryMovementType.EXIT),
    EXPIRED_OR_SPOILAGE(InventoryMovementType.EXIT),
    DAMAGED_OR_LOSS(InventoryMovementType.EXIT),
    SALE(InventoryMovementType.EXIT),
    INTERNAL_CONSUMPTION(InventoryMovementType.EXIT),
    SUPPLIER_RETURN(InventoryMovementType.EXIT),
    TRANSFER_OUT(InventoryMovementType.EXIT),

    // Ajustes y Correcciones
    PHYSICAL_COUNT(InventoryMovementType.ADJUSTMENT),
    CORRECTION(InventoryMovementType.ADJUSTMENT),
    OTHER(InventoryMovementType.ADJUSTMENT);

    private final InventoryMovementType expectedMovementType;

    InventoryMovementReason(InventoryMovementType expectedMovementType) {
        this.expectedMovementType = expectedMovementType;
    }

    public InventoryMovementType getExpectedMovementType() {
        return expectedMovementType;
    }

    public boolean isCompatibleWith(InventoryMovementType movementType) {
        return movementType != null && this.expectedMovementType == movementType;
    }
}
