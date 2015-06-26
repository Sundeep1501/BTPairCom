package com.sundeep.btpaircom;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Handler.Callback;
import android.os.Message;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class CommunicationActivity extends Activity {

	private final static String UUID_SPP = "00001101-0000-1000-8000-00805F9B34FB";
	List<String> uuids = new ArrayList<String>();

	private BluetoothDevice device;
	private boolean isBonded;

	Button pairButton, connectButton;

	ConnectThread connectThread;
	ConnectedThread connectedThread;

	private ProgressDialog dialog;

	private static final int MESSAGE_READ = 789;
	private static final int MESSAGE_WRITE = 790;

	private View commandSendButton;
	private EditText commandEditText;

	private ListView commandHistoryListView;
	private ArrayAdapter<String> arrayAdapter;
	private List<String> commandHistory = new ArrayList<String>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_bluetooth_device);

		device = getIntent().getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

		isBonded = device.getBondState() == BluetoothDevice.BOND_BONDED;

		updateDeviceInfo();

		pairButton = (Button) findViewById(R.id.device_pair);
		connectButton = (Button) findViewById(R.id.device_connect);

		pairButton.setOnClickListener(onClickListener);
		connectButton.setOnClickListener(onClickListener);

		if (isBonded) {
			fetchUuidsSdp();
		} else {
			connectButton.setVisibility(View.GONE);
		}

		commandEditText = (EditText) findViewById(R.id.device_command);
		commandEditText.setVisibility(View.GONE);

		commandSendButton = findViewById(R.id.device_send_command);
		commandSendButton.setOnClickListener(onClickListener);
		commandSendButton.setVisibility(View.GONE);

		commandHistoryListView = (ListView) findViewById(R.id.deviceCommandHistory);
		arrayAdapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_list_item_1, commandHistory);
		commandHistoryListView.setAdapter(arrayAdapter);
	}

	private void fetchUuidsSdp() {
		device.fetchUuidsWithSdp();
		pairButton.setVisibility(View.GONE);
		IntentFilter intentFilter = new IntentFilter(
				BluetoothDevice.ACTION_UUID);
		registerReceiver(serviceUUIDReceiver, intentFilter);
	}

	private void updateDeviceInfo() {
		String string = "Device Information:\n" + device.getName() + "\n"
				+ device.getAddress() + "\n" + device.getBluetoothClass()
				+ "\n" + device.getBondState();
		TextView info = (TextView) findViewById(R.id.device_info);
		info.setText(string);
	}

	Handler handler = new Handler(new Callback() {

		@Override
		public boolean handleMessage(Message msg) {
			byte[] buffer = new byte[msg.arg1];
			String byteString = "";
			switch (msg.what) {
			case MESSAGE_READ:
				for (int i = 0; i < buffer.length; i++) {
					buffer[i] = ((byte[]) msg.obj)[i];
					byteString = byteString + "," + buffer[i];
				}
				Log.e("Bluetooth", "Read buffer " + byteString);
				commandHistory.add(0,
						"Response:\nHex:" + Util.bytesToHex(buffer)
								+ "\nBytes:" + byteString);
				arrayAdapter.notifyDataSetChanged();
				break;
			case MESSAGE_WRITE:
				Log.e("Bluetooth",
						"write buffer " + Util.bytesToHex((byte[]) msg.obj));
				commandHistory.add(0, "\n\n\n");
				commandHistory.add(0,
						"Sent:\nHex:" + Util.bytesToHex((byte[]) msg.obj));
				arrayAdapter.notifyDataSetChanged();
				break;
			default:
				break;
			}

			return false;
		}
	});

	@Override
	protected void onDestroy() {
		super.onDestroy();
		if (connectThread != null) {
			connectThread.cancel();
		}
		if (connectedThread != null) {
			connectedThread.cancel();
		}
	}

	private BroadcastReceiver serviceUUIDReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			Parcelable[] uuidExtra = intent
					.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
			String services = "Service SDP UUIDs Not found";
			if (uuidExtra != null) {
				services = "Service SDP UUIDs:";
				for (int i = 0; i < uuidExtra.length; i++) {
					uuids.add(uuidExtra[i].toString());
					services = services + "\n" + uuidExtra[i].toString();
				}
			}
			((TextView) findViewById(R.id.device_serial_no)).setText(services);
			unregisterReceiver(serviceUUIDReceiver);
		}
	};

	private BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			String pairedStatus;
			if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
				pairButton.setVisibility(View.GONE);
				connectButton.setVisibility(View.VISIBLE);
				pairedStatus = "Paired Successfully";
				updateDeviceInfo();
				fetchUuidsSdp();
			} else {
				pairButton.setVisibility(View.VISIBLE);
				connectButton.setVisibility(View.GONE);
				pairedStatus = "Paired Failed";
			}
			Toast.makeText(CommunicationActivity.this, pairedStatus,
					Toast.LENGTH_SHORT).show();
			unregisterReceiver(bondStateReceiver);
		}
	};

	OnClickListener onClickListener = new OnClickListener() {

		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.device_pair:
				IntentFilter intentFilter = new IntentFilter(
						BluetoothDevice.ACTION_BOND_STATE_CHANGED);
				registerReceiver(bondStateReceiver, intentFilter);

				try {
					Class<? extends BluetoothDevice> btClass = device
							.getClass();
					Method createBondMethod = btClass.getMethod("createBond");
					Boolean returnValue = (Boolean) createBondMethod
							.invoke(device);
					Log.i("Bluetooth",
							"Bond invoke status " + returnValue.booleanValue());
				} catch (Exception e) {
					e.printStackTrace();
					unregisterReceiver(bondStateReceiver);
				}
				break;
			case R.id.device_connect:
				dialog = new ProgressDialog(CommunicationActivity.this);
				dialog.setMessage("Connecting...");
				dialog.setCancelable(false);
				dialog.setCanceledOnTouchOutside(false);
				dialog.show();
				connectThread = new ConnectThread();
				connectThread.start();
				break;
			case R.id.device_send_command:
				String string = commandEditText.getText().toString();
				// String setTime = "5512030E1E0000000000000000000000000000EC";
				connectedThread.write(Util.hexStringToByteArray(string));
				break;
			default:
				break;
			}

		}
	};

	private class ConnectThread extends Thread {
		private final BluetoothSocket mmSocket;

		public ConnectThread() {
			BluetoothSocket tmp = null;
			try {
				UUID uuid = UUID.fromString(UUID_SPP);
				if (Build.VERSION.SDK_INT >= 10) {
					tmp = device
							.createInsecureRfcommSocketToServiceRecord(uuid);
				} else {
					tmp = device.createRfcommSocketToServiceRecord(uuid);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
			mmSocket = tmp;
		}

		public void run() {
			BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
			try {
				mmSocket.connect();
			} catch (IOException connectException) {
				try {
					mmSocket.close();
				} catch (IOException closeException) {
				}
				dismissConnectionDialog(false);
				return;
			}

			manageConnectedSocket(mmSocket);
			Log.e("Bluetooth Socket", "Connected ");
		}

		/** Will cancel an in-progress connection, and close the socket */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

	public void manageConnectedSocket(BluetoothSocket mmSocket) {
		connectedThread = new ConnectedThread(mmSocket);
		connectedThread.start();
		// powerDownDevice();
	}

	public void dismissConnectionDialog(final boolean isConnected) {
		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				String message;
				if (isConnected) {
					message = "Connected Successfully";
					connectButton.setVisibility(View.GONE);
					commandEditText.setVisibility(View.VISIBLE);
					commandSendButton.setVisibility(View.VISIBLE);
				} else {
					message = "Failed to connect";
				}
				Toast.makeText(CommunicationActivity.this, message,
						Toast.LENGTH_SHORT).show();
				dialog.dismiss();
			}
		});
	}

	private class ConnectedThread extends Thread {

		private final BluetoothSocket mmSocket;
		private final InputStream mmInStream;
		private final OutputStream mmOutStream;

		public ConnectedThread(BluetoothSocket socket) {
			mmSocket = socket;
			InputStream tmpIn = null;
			OutputStream tmpOut = null;

			// Get the input and output streams, using temp objects because
			// member streams are final
			try {
				tmpIn = socket.getInputStream();
				tmpOut = socket.getOutputStream();
			} catch (IOException e) {
			}

			mmInStream = tmpIn;
			mmOutStream = tmpOut;
			dismissConnectionDialog(true);
		}

		public void run() {
			byte[] buffer = new byte[1024]; // buffer store for the stream
			int bytes; // bytes returned from read()

			// Keep listening to the InputStream until an exception occurs
			while (true) {
				try {
					// Read from the InputStream
					bytes = mmInStream.read(buffer);
					// Send the obtained bytes to the UI activity
					handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer)
							.sendToTarget();
				} catch (IOException e) {
					break;
				}
			}
		}

		/* Call this from the main activity to send data to the remote device */
		public void write(byte[] bytes) {
			try {
				mmOutStream.write(bytes);
				handler.obtainMessage(MESSAGE_WRITE, bytes.length, -1, bytes)
						.sendToTarget();
			} catch (IOException e) {
			}
		}

		/* Call this from the main activity to shutdown the connection */
		public void cancel() {
			try {
				mmSocket.close();
			} catch (IOException e) {
			}
		}
	}

}
