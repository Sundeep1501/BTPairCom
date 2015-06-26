package com.sundeep.btpaircom;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

public class ScanningActivity extends Activity {
	private Button buttonSearch, buttonOn, buttonOff;
	private ArrayAdapter<String> deviceAdapter;
	private ArrayList<String> deviceNames;
	private Map<String, BluetoothDevice> bluetoothDevices;
	private BluetoothAdapter bluetoothAdapter;
	private ProgressDialog dialog;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		buttonSearch = (Button) findViewById(R.id.buttonSearch);
		buttonOn = (Button) findViewById(R.id.buttonOn);
		buttonOff = (Button) findViewById(R.id.buttonOff);
		buttonOn.setOnClickListener(buttonClickListener);
		buttonSearch.setOnClickListener(buttonClickListener);
		buttonOff.setOnClickListener(buttonClickListener);

		deviceNames = new ArrayList<String>();
		deviceAdapter = new ArrayAdapter<String>(ScanningActivity.this,
				android.R.layout.simple_list_item_1, deviceNames);
		ListView listView = (ListView) findViewById(R.id.listViewPaired);
		listView.setAdapter(deviceAdapter);
		listView.setOnItemClickListener(devicesPairedClickListener);

		bluetoothDevices = new HashMap<String, BluetoothDevice>();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		if (bluetoothAdapter == null) {
			Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT)
					.show();
			finish();
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		manageControlButtons();
	}

	private void manageControlButtons() {
		if (bluetoothAdapter.isEnabled()) {
			buttonOn.setEnabled(false);
			buttonOff.setEnabled(true);
			if (bluetoothAdapter.isDiscovering()) {
				buttonSearch.setEnabled(false);
			} else {
				buttonSearch.setEnabled(true);
			}
		} else {
			buttonOn.setEnabled(true);
			buttonOff.setEnabled(false);
			buttonSearch.setEnabled(false);
		}
	}

	OnClickListener buttonClickListener = new OnClickListener() {

		@Override
		public void onClick(View view) {
			switch (view.getId()) {
			case R.id.buttonOn:
				IntentFilter stateFilter = new IntentFilter(
						BluetoothAdapter.ACTION_STATE_CHANGED);
				registerReceiver(onOffReceiver, stateFilter);
				bluetoothAdapter.enable();
				break;
			case R.id.buttonSearch:
				discoverDevices();
				break;
			case R.id.buttonOff:
				IntentFilter filter = new IntentFilter(
						BluetoothAdapter.ACTION_STATE_CHANGED);
				registerReceiver(onOffReceiver, filter);
				bluetoothAdapter.disable();
				break;
			default:
				break;
			}
		}
	};

	private void discoverDevices() {
		dialog = new ProgressDialog(this);
		dialog.setMessage("Scanning...");
		dialog.setCancelable(false);
		dialog.setCanceledOnTouchOutside(false);
		dialog.show();

		deviceNames.clear();
		bluetoothDevices.clear();
		deviceAdapter.notifyDataSetChanged();

		IntentFilter intentFilter = new IntentFilter(
				BluetoothDevice.ACTION_FOUND);
		registerReceiver(devicefoundReceiver, intentFilter);
		intentFilter = new IntentFilter(
				BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
		registerReceiver(discoverDevicesCompletedReceiver, intentFilter);

		bluetoothAdapter.startDiscovery();
	}

	OnItemClickListener devicesPairedClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			BluetoothDevice bdDevice = bluetoothDevices.get(deviceNames.get(
					position).split("\n")[1]);
			Intent intent = new Intent(ScanningActivity.this,
					CommunicationActivity.class);
			intent.putExtra(BluetoothDevice.EXTRA_DEVICE, bdDevice);
			startActivity(intent);
		}
	};

	private BroadcastReceiver devicefoundReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			BluetoothDevice device = intent
					.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			if (bluetoothDevices.get(device.getAddress()) == null) {
				deviceNames.add(device.getName() + "\n" + device.getAddress());
			}
			bluetoothDevices.put(device.getAddress(), device);
			deviceAdapter.notifyDataSetChanged();
		}
	};

	private BroadcastReceiver discoverDevicesCompletedReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			unregisterReceiver(devicefoundReceiver);
			unregisterReceiver(discoverDevicesCompletedReceiver);
			if (dialog != null) {
				dialog.dismiss();
			}
		}
	};

	private BroadcastReceiver onOffReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE,
					BluetoothAdapter.ERROR);
			switch (state) {
			case BluetoothAdapter.STATE_OFF:
			case BluetoothAdapter.STATE_ON:
				unregisterReceiver(onOffReceiver);
				manageControlButtons();
				break;
			}
		}
	};

}