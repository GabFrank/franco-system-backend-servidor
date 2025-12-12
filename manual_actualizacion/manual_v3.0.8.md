
## Farmacia ##
cp /home/franco/farma/FRC/frc-server/frc-server.jar /home/franco/farma/FRC/frc-server/frc-server_bkp.jar
rm /home/franco/farma/FRC/frc-server/frc-server.jar
curl -L -o /home/franco/farma/FRC/frc-server/frc-server.jar https://github.com/GabFrank/franco-system-backend-servidor/releases/download/3.0.8-stable/frc-server.jar
sudo systemctl restart frc-farmacia.service && journalctl -f -u frc-farmacia.service
