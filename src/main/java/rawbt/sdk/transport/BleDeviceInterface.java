package rawbt.sdk.transport;

import java.util.UUID;

public interface BleDeviceInterface {
    UUID[] getWriteUuid();
    UUID[] getNotifyUuid();
}
