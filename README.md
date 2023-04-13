# qrdisplay-sdk

```
qrDisplay = new MertechQrDisplay(this);


String res = qrDisplay.connectDevice(new ConnectParameters() {
                    @Override
                    public int getProtocol() {
                        try {
                            return parameters.getProtocol();
                        } catch (RemoteException e) {
                            return Constant.PROTOCOL_NOT_SET;
                        }
                    }

                    @Override
                    public String get_mac() {
                        try {
                            return parameters.getMac();
                        } catch (RemoteException e) {
                            return "00:00:00:00:00:00";
                        }
                    }

                    @Override
                    public String get_vid() {
                        try {
                            return parameters.getVid();
                        } catch (RemoteException e) {
                            return "0";
                        }
                    }

                    @Override
                    public String get_pid() {
                        try {
                            return parameters.getPid();
                        } catch (RemoteException e) {
                            return "0";
                        }
                    }
                });
// command                
qrDisplay.qrCode(qr);
qrDisplay.symbolOk();
qrDisplay.symbolFail();
qrDisplay.cls();
qrDisplay.sendBytes(bytes);

```
