package com.smarthome.ioT.device;

import com.smarthome.enums.IoTType;
import com.smarthome.ioT.IoTServerImpl;
import com.smarthome.ioT.gateway.GatewayServer;
import com.smarthome.model.Device;
import com.smarthome.model.IoT;
import com.smarthome.model.config.DeviceConfig;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.UUID;

public class DeviceServerImpl extends IoTServerImpl implements DeviceServer {

    public DeviceServerImpl(final DeviceConfig deviceConfig) throws RemoteException {
        super(deviceConfig, true);
    }

    @Override
    public IoT createIoT() {
        return new Device(UUID.randomUUID(), IoTType.DEVICE, getDeviceConfig().getDeviceType());
    }

    private DeviceConfig getDeviceConfig() {
        return ((DeviceConfig) getServerConfig());
    }

    private Device getDevice() {
        return ((Device) getIoT());
    }

    @Override
    protected String getName() {
        return NAME;
    }

    @Override
    public void queryState(final long senderLogicalTime, final UUID senderId) throws RemoteException {
        incrementLogicalTime(senderLogicalTime, senderId);

        try {
            GatewayServer.connect(getDeviceConfig().getGatewayAddress())
                    .reportState(getDevice(), getSynchronizedTime(), getLogicalTime(),
                            getIoT().getId());
        } catch (NotBoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setState(final boolean state, final long senderLogicalTime,
            final UUID senderId) throws RemoteException {
        if (getDevice().getState() == state) {
            return;
        }

        getDevice().setState(state);
        System.out.println(getDevice() + " switched "
                + (getDevice().getState() ? "on" : "off") + ".");

        queryState(senderLogicalTime, senderId);
    }

    @Override
    public void toggleState() throws RemoteException {
        getDevice().setState(!getDevice().getState());
        queryState(0, null);
    }
}
