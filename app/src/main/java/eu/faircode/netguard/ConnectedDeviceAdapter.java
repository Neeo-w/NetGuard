
package eu.faircode.netguard;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;

public class ConnectedDeviceAdapter extends BaseAdapter {
    private Context context;
    private List<ActivityTethering.ConnectedDevice> devices;
    private LayoutInflater inflater;

    public ConnectedDeviceAdapter(Context context, List<ActivityTethering.ConnectedDevice> devices) {
        this.context = context;
        this.devices = devices;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public int getCount() {
        return devices.size();
    }

    @Override
    public Object getItem(int position) {
        return devices.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_connected_device, parent, false);
            holder = new ViewHolder();
            holder.tvDeviceName = convertView.findViewById(R.id.tv_device_name);
            holder.tvDeviceAddress = convertView.findViewById(R.id.tv_device_address);
            holder.tvBytesTransferred = convertView.findViewById(R.id.tv_bytes_transferred);
            holder.tvStatus = convertView.findViewById(R.id.tv_device_status);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        ActivityTethering.ConnectedDevice device = devices.get(position);
        holder.tvDeviceName.setText(device.name != null ? device.name : "Unknown Device");
        holder.tvDeviceAddress.setText(device.address);
        holder.tvBytesTransferred.setText(formatBytes(device.bytesTransferred));
        holder.tvStatus.setText(device.isActive ? "Active" : "Inactive");
        holder.tvStatus.setTextColor(context.getResources().getColor(
                device.isActive ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));

        return convertView;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    static class ViewHolder {
        TextView tvDeviceName;
        TextView tvDeviceAddress;
        TextView tvBytesTransferred;
        TextView tvStatus;
    }
}
