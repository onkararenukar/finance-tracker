import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Paper,
  Typography,
  Box,
  Button,
  TextField,
  CircularProgress,
  Alert,
} from '@mui/material';
import { CloudUpload } from '@mui/icons-material';

const UploadStatement: React.FC = () => {
  const [file, setFile] = useState<File | null>(null);
  const [bankName, setBankName] = useState('');
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState(false);
  const navigate = useNavigate();

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      setFile(e.target.files[0]);
    }
  };

  const handleUpload = async () => {
    if (!file || !bankName) {
      setError('Please select a file and enter bank name');
      return;
    }

    setUploading(true);
    setError('');
    setSuccess(false);

    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('bankName', bankName);

      const response = await fetch('http://localhost:8080/api/v1/statements', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
        },
        body: formData,
      });

      if (!response.ok) {
        throw new Error('Upload failed');
      }

      setSuccess(true);
      setTimeout(() => {
        setSuccess(false);
        navigate('/statements');
      }, 2000);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <Container maxWidth="md">
      <Paper elevation={3} sx={{ p: 4, mt: 4 }}>
        <Typography variant="h4" gutterBottom>
          Upload Bank Statement
        </Typography>

        {error && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {error}
          </Alert>
        )}

        {success && (
          <Alert severity="success" sx={{ mb: 2 }}>
            Statement uploaded successfully! Redirecting...
          </Alert>
        )}

        <Box sx={{ mt: 3 }}>
          <TextField
            fullWidth
            label="Bank Name"
            value={bankName}
            onChange={(e) => setBankName(e.target.value)}
            margin="normal"
            required
          />

          <Box
            sx={{
              border: '2px dashed #ccc',
              borderRadius: 2,
              p: 4,
              mt: 2,
              textAlign: 'center',
              cursor: 'pointer',
              '&:hover': { borderColor: 'primary.main' },
            }}
            onClick={() => document.getElementById('file-input')?.click()}
          >
            <input
              id="file-input"
              type="file"
              accept=".pdf,.csv"
              onChange={handleFileChange}
              style={{ display: 'none' }}
            />
            <CloudUpload sx={{ fontSize: 48, color: 'primary.main' }} />
            <Typography variant="body1" sx={{ mt: 1 }}>
              {file ? file.name : 'Click to select PDF or CSV file'}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Supports PDF (including scanned documents with OCR) and CSV files
            </Typography>
          </Box>

          <Button
            variant="contained"
            fullWidth
            onClick={handleUpload}
            disabled={uploading || !file || !bankName}
            sx={{ mt: 3 }}
            startIcon={uploading ? <CircularProgress size={20} /> : <CloudUpload />}
          >
            {uploading ? 'Uploading...' : 'Upload Statement'}
          </Button>
        </Box>
      </Paper>
    </Container>
  );
};

export default UploadStatement;
