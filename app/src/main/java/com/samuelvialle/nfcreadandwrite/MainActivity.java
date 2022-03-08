package com.samuelvialle.nfcreadandwrite;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MainActivity extends AppCompatActivity {

    // ******* Vars globales
    // Textes des messages (pourrait-être dans un fichier de constants)
    public static final String ERROR_DETECTED = "No NFC Tag detected";
    public static final String WRITE_ERROR = "Error during writing, Try Again! Error is : %1$s";
    public static final String WRITE_SUCCESS = "Tag written succesfully!";

    // Vars de fonctionnement
    NfcAdapter nfcAdapter;
    PendingIntent pendingIntent;
    IntentFilter writingTag[];
    boolean writeMode;
    Tag myTag;
    Context context;

    // Les vars des widgets
    EditText etNfcTag;
    Button btnWriteTag;
    TextView tvNfcContents;

    // ******* Init des widgets
    private void initUI() {
        etNfcTag = findViewById(R.id.etNfcTag);
        btnWriteTag = findViewById(R.id.btnWriteTag);
        tvNfcContents = findViewById(R.id.tvNfcContents);
        context = this;
    }

    // Init du NFC et de son adapter
    private void initNfc() {
        // Init de l'adapter nfc
        nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        // Gestion des terminaux sans NFC
        if (nfcAdapter == null) {
            Toast.makeText(context, "This device does not support NFC", Toast.LENGTH_SHORT).show();
            finish();
        }
        readFromIntent(getIntent());
        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT);
        writingTag = new IntentFilter[]{tagDetected};
    }

    // Lecture du tag
    private void readFromIntent(Intent intent) {
        String action = intent.getAction();
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)
                || NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
            Parcelable[] rawTags = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            NdefMessage[] tags = null;
            if (tags != null) {
                tags = new NdefMessage[rawTags.length];
                for (int i = 0; i < tags.length; i++) {
                    tags[i] = (NdefMessage) rawTags[i];
                }
            }
            buildTagViews(tags);
        }
    }

    private void buildTagViews(NdefMessage[] tags) {
        if (tags == null || tags.length == 0) return;

        String s = "";
        byte[] payload = tags[0].getRecords()[0].getPayload();
        String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8" : "UTF-16"; // Vérification de la méthode utilisée pour encoder le texte lu
        int languageCodeLength = payload[0] & 0063; // Get the language code ici EN

        try {
            // On récupère le texte du module NFC
            s = new String(payload, languageCodeLength + 1, payload.length - languageCodeLength - 1, textEncoding);
        } catch (UnsupportedEncodingException e) {
            Log.e("UnsupportedEncoding", e.toString());
        }
        tvNfcContents.setText(s);
    }

    private void writeTag(String s, Tag tag) throws IOException, FormatException {
        NdefRecord[] records = {createRecord(s)};
        NdefMessage message = new NdefMessage(records);
        // Instance de Ndef pour le tag
        Ndef ndef = Ndef.get(tag);
        // Enable I/O
        ndef.connect();
        // Write message
        ndef.writeNdefMessage(message);
        // Close connection
        ndef.close();
    }

    private NdefRecord createRecord(String s) throws UnsupportedEncodingException {
        String lang = "en";
        byte[] textBytes = s.getBytes();
        byte[] langBytes = lang.getBytes("US-ASCII");
        int langLength = langBytes.length;
        int textLength = textBytes.length;
        byte[] payload = new byte[1 + langLength + textLength];

        // Set status byte (see NDEF spec for actual bytes)
        payload[0] = (byte) langLength;

        // copy langbytes and textbytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength);
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);

        NdefRecord ndefRecord = new NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, new byte[0], payload);

        return ndefRecord;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
        }
    }

    private void writeModeOn() {
        writeMode = true;
        if (nfcAdapter == null) {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        } else {
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, writingTag, null);
        }
    }

    private void writeModeOff() {
        writeMode = false;
        if (nfcAdapter == null) {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this);
        } else {
            nfcAdapter.disableForegroundDispatch(this);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        writeModeOff();
    }

    @Override
    protected void onResume() {
        super.onResume();
        writeModeOn();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initUI();
        initNfc();

        // Gestion du clic sur le bouton
        btnWriteTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    if (myTag == null) {
                        Toast.makeText(context, ERROR_DETECTED, Toast.LENGTH_SHORT).show();
                    } else {
                        writeTag("Present tag is: " + etNfcTag.getText().toString(), myTag);
                        Toast.makeText(context, WRITE_SUCCESS, Toast.LENGTH_SHORT).show();
                    }
                } catch (IOException e) {
                    Toast.makeText(context, WRITE_ERROR + e, Toast.LENGTH_SHORT).show();
                } catch (FormatException e) {
                    Toast.makeText(context, WRITE_ERROR + e, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }


}





















