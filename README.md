# How to run

```bash
cd <docker-folder>
sudo docker compose build
sudo docker compose up
```

To enter PIN or trigger sensors, attach to their shells and send commands:
```bash
cd <docker-folder>
# Enter PIN
sudo docker compose attach keypad
enterpin 1234 kitchen
# Trigger a sensor
sudo docker compose attach sensor1
trigger
```

To verify the recovery of the control unit, restart the container or make it crash (with special pin 666):
```bash
cd <docker-folder>
# Restart
sudo docker compose restart cu
# Trigger an exception
sudo docker compose attach keypad
enterpin 666 none
```