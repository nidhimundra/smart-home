package com.smarthome.gateway.server;

import com.smarthome.db.server.DbServer;
import com.smarthome.device.server.DeviceServer;
import com.smarthome.model.Address;
import com.smarthome.model.IoT;
import com.smarthome.model.Device;
import com.smarthome.model.sensor.DoorSensor;
import com.smarthome.model.sensor.MotionSensor;
import com.smarthome.model.sensor.Sensor;
import com.smarthome.model.sensor.TemperatureSensor;
import com.smarthome.sensor.server.SensorServer;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GatewayServerImpl implements GatewayServer {

    private final Map<UUID, Address> registeredIoTs;
    private final Address gatewayAddress;
    private final Address dbAddress;

    private long synchronizationOffset;

    public GatewayServerImpl(final Address gatewayAddress, final Address dbAddress) {
        this.gatewayAddress = gatewayAddress;
        this.dbAddress = dbAddress;

        registeredIoTs = new HashMap<>();
    }

    @Override
    public IoT register(final IoT ioT, final Address address) throws RemoteException {
        final UUID uuid = getRandomUUID();
        registeredIoTs.put(uuid, address);

        switch (ioT.getIoTType()) {
            case SENSOR:
                Sensor sensor = ((Sensor) ioT);

                switch (sensor.getSensorType()) {
                    case TEMPERATURE:
                        sensor = new TemperatureSensor(uuid, sensor.getIoTType(), sensor.getSensorType());
                        break;

                    case MOTION:
                        sensor = new MotionSensor(uuid, sensor.getIoTType(), sensor.getSensorType());
                        break;

                    case DOOR:
                        sensor = new DoorSensor(uuid, sensor.getIoTType(), sensor.getSensorType());
                        break;
                }

                return sensor;

            case DEVICE:
                Device device = (Device) ioT;
                return new Device(uuid, device.getIoTType(), device.getDeviceType());
        }

        return null;
    }

    @Override
    public void queryState(final IoT ioT) {
        if (registeredIoTs.containsKey(ioT.getId())) {
            try {
                SensorServer.connect(registeredIoTs.get(ioT.getId())).queryState();
            } catch (RemoteException | NotBoundException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void reportState(final IoT ioT) throws RemoteException {
        DbServer dbServer = null;

        try {
            dbServer = DbServer.connect(getDbAddress());
        } catch (NotBoundException e) {
            e.printStackTrace();
        }

        switch (ioT.getIoTType()) {
            case SENSOR:
                final Sensor sensor = ((Sensor) ioT);

                switch (sensor.getSensorType()) {
                    case TEMPERATURE:
                        final TemperatureSensor temperatureSensor = ((TemperatureSensor) sensor);
                        assert dbServer != null;
                        dbServer.temperatureChanged(temperatureSensor, getSynchronizedTime());
                        break;

                    case MOTION:
                        final MotionSensor motionSensor = ((MotionSensor) sensor);
                        assert dbServer != null;
                        dbServer.motionDetected(motionSensor, getSynchronizedTime());
                        break;

                    case DOOR:
                        final DoorSensor doorSensor = ((DoorSensor) sensor);
                        assert dbServer != null;
                        dbServer.doorToggled(doorSensor, getSynchronizedTime());
                        break;
                }
                break;

            case DEVICE:
                final Device device = ((Device) ioT);
                assert dbServer != null;
                dbServer.deviceToggled(device, getSynchronizedTime());

                break;
        }
    }

    @Override
    public void changeDeviceState(final Device device, final boolean state) {
        try {
            DeviceServer.connect(registeredIoTs.get(device.getId())).changeState(state);
        } catch (RemoteException | NotBoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Generates a random UUID which is not present in {@code registeredIoTs}.
     *
     * @return The randomly generated UUID
     */
    private UUID getRandomUUID() {
        UUID uuid = UUID.randomUUID();
        if (registeredIoTs.containsKey(uuid)) {
            uuid = getRandomUUID();
        }
        return uuid;
    }

    private Address getGatewayAddress() {
        return gatewayAddress;
    }

    private Address getDbAddress() {
        return dbAddress;
    }

    /**
     * Returns the System's current time after adjustment by adding an offset,
     * calculated using the
     * <a href="https://en.wikipedia.org/wiki/Berkeley_algorithm">Berkeley algorithm</a>
     * for clock synchronization.
     *
     * @return The offset-adjusted System time
     */
    private long getSynchronizedTime() {
        return System.currentTimeMillis() + getSynchronizationOffset();
    }

    private long getSynchronizationOffset() {
        return synchronizationOffset;
    }
}