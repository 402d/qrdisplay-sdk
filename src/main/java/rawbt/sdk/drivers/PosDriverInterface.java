package rawbt.sdk.drivers;

import androidx.annotation.NonNull;
import rawbt.sdk.transport.Transport;

public interface PosDriverInterface extends Transport.CallBack{
   // -----------------------
   interface TaskForTransport {
      boolean isCancelled();
      void deviceProgress(String message);
      void deviceError(String string);
      void receiveFromDevice(byte[] b);
      void sentToDevice(byte[] b);
      void onTransportConnect();
      void onTransportDisconnect();
   }
   boolean isCancelled();
   void interactiveTask(@NonNull TaskForTransport jobTask);
   // -------------------
   boolean isError();
   void resetError();
   String getErrorMessage();
}

