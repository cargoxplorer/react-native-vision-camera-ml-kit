import { Link } from 'expo-router';
import { View, Text, StyleSheet, ScrollView, Platform } from 'react-native';
import { StatusBar } from 'expo-status-bar';

export default function HomeScreen() {
  return (
    <ScrollView style={styles.container}>
      <StatusBar style="auto" />

      <View style={styles.header}>
        <Text style={styles.title}>React Native Vision Camera</Text>
        <Text style={styles.subtitle}>ML Kit Integration Examples</Text>
      </View>

      <View style={styles.section}>
        <Text style={styles.sectionTitle}>Features</Text>

        <Link href="/text-recognition" style={styles.card}>
          <View style={styles.cardContent}>
            <Text style={styles.cardIcon}>📝</Text>
            <View style={styles.cardText}>
              <Text style={styles.cardTitle}>Text Recognition v2</Text>
              <Text style={styles.cardDescription}>
                On-device OCR with 5 language scripts
              </Text>
              <Text style={styles.cardPlatform}>Android • iOS</Text>
            </View>
          </View>
        </Link>

        <Link href="/barcode-scanner" style={styles.card}>
          <View style={styles.cardContent}>
            <Text style={styles.cardIcon}>📊</Text>
            <View style={styles.cardText}>
              <Text style={styles.cardTitle}>Barcode Scanning</Text>
              <Text style={styles.cardDescription}>
                Scan all 1D and 2D formats with structured data
              </Text>
              <Text style={styles.cardPlatform}>Android • iOS</Text>
            </View>
          </View>
        </Link>

        <Link href="/document-scanner" style={styles.card}>
          <View style={styles.cardContent}>
            <Text style={styles.cardIcon}>📄</Text>
            <View style={styles.cardText}>
              <Text style={styles.cardTitle}>Document Scanner</Text>
              <Text style={styles.cardDescription}>
                Professional document digitization with ML cleaning
              </Text>
              <Text style={styles.cardPlatform}>
                {Platform.OS === 'android'
                  ? 'Android Only ✓'
                  : 'Android Only ✗'}
              </Text>
            </View>
          </View>
        </Link>

        <Link href="/vin-scanner" style={styles.card}>
          <View style={styles.cardContent}>
            <Text style={styles.cardIcon}>🚗</Text>
            <View style={styles.cardText}>
              <Text style={styles.cardTitle}>VIN Scanner</Text>
              <Text style={styles.cardDescription}>
                Combined barcode & OCR VIN identification with confirmation
              </Text>
              <Text style={styles.cardPlatform}>Android • iOS</Text>
            </View>
          </View>
        </Link>
      </View>

      <View style={styles.footer}>
        <Text style={styles.footerText}>
          Powered by Google ML Kit & React Native Vision Camera
        </Text>
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  header: {
    padding: 20,
    backgroundColor: '#fff',
    borderBottomWidth: 1,
    borderBottomColor: '#e0e0e0',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    color: '#333',
  },
  subtitle: {
    fontSize: 14,
    color: '#666',
    marginTop: 4,
  },
  section: {
    padding: 16,
  },
  sectionTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 12,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 12,
    padding: 16,
    marginBottom: 12,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
    elevation: 3,
  },
  cardContent: {
    flexDirection: 'row',
    alignItems: 'center',
  },
  cardIcon: {
    fontSize: 40,
    marginRight: 16,
  },
  cardText: {
    flex: 1,
  },
  cardTitle: {
    fontSize: 18,
    fontWeight: '600',
    color: '#333',
    marginBottom: 4,
  },
  cardDescription: {
    fontSize: 14,
    color: '#666',
    marginBottom: 4,
  },
  cardPlatform: {
    fontSize: 12,
    color: '#999',
    fontWeight: '500',
  },
  footer: {
    padding: 20,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 12,
    color: '#999',
    textAlign: 'center',
  },
});
