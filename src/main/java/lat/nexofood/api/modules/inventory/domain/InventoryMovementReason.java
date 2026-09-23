package lat.nexofood.api.modules.inventory.domain;

public enum InventoryMovementReason {
    // Entradas
    PURCHASE,
    INITIAL_STOCK,
    CUSTOMER_RETURN,
    TRANSFER_IN,

    // Salidas y Mermas
    KITCHEN_CONSUMPTION_OR_WASTE,
    EXPIRED_OR_SPOILAGE,
    DAMAGED_OR_LOSS,
    SALE,
    INTERNAL_CONSUMPTION,
    SUPPLIER_RETURN,
    TRANSFER_OUT,

    // Ajustes y Correcciones
    PHYSICAL_COUNT,
    CORRECTION,
    OTHER
}
