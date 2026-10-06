import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useNavigate } from 'react-router-dom';
import {
  Container,
  Grid,
  Paper,
  Typography,
  Button,
  Box,
  AppBar,
  Toolbar,
  IconButton,
} from '@mui/material';
import {
  AccountBalance,
  Description,
  Category,
  ExitToApp,
  UploadFile,
} from '@mui/icons-material';

const Dashboard: React.FC = () => {
  const { user, logout, isAdmin } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <Box sx={{ flexGrow: 1 }}>
      <AppBar position="static">
        <Toolbar>
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            Finance Tracker
          </Typography>
          <Typography variant="body1" sx={{ mr: 2 }}>
            {user?.fullName} ({user?.role})
          </Typography>
          <IconButton color="inherit" onClick={handleLogout}>
            <ExitToApp />
          </IconButton>
        </Toolbar>
      </AppBar>

      <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
        <Typography variant="h4" gutterBottom>
          Welcome, {user?.fullName}!
        </Typography>

        <Grid container spacing={3}>
          <Grid item xs={12} sm={6} md={3}>
            <Paper
              sx={{ p: 2, display: 'flex', flexDirection: 'column', height: 140, cursor: 'pointer' }}
              onClick={() => navigate('/statements')}
            >
              <Description sx={{ fontSize: 40, color: 'primary.main' }} />
              <Typography variant="h6" sx={{ mt: 1 }}>
                Bank Statements
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Upload and manage statements
              </Typography>
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper
              sx={{ p: 2, display: 'flex', flexDirection: 'column', height: 140, cursor: 'pointer' }}
              onClick={() => navigate('/transactions')}
            >
              <AccountBalance sx={{ fontSize: 40, color: 'success.main' }} />
              <Typography variant="h6" sx={{ mt: 1 }}>
                Transactions
              </Typography>
              <Typography variant="body2" color="text.secondary">
                View all transactions
              </Typography>
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper
              sx={{ p: 2, display: 'flex', flexDirection: 'column', height: 140, cursor: 'pointer' }}
              onClick={() => navigate('/categories')}
            >
              <Category sx={{ fontSize: 40, color: 'warning.main' }} />
              <Typography variant="h6" sx={{ mt: 1 }}>
                Categories
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Manage transaction categories
              </Typography>
            </Paper>
          </Grid>

          <Grid item xs={12} sm={6} md={3}>
            <Paper
              sx={{ p: 2, display: 'flex', flexDirection: 'column', height: 140, cursor: 'pointer' }}
              onClick={() => navigate('/upload')}
            >
              <UploadFile sx={{ fontSize: 40, color: 'info.main' }} />
              <Typography variant="h6" sx={{ mt: 1 }}>
                Upload Statement
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Add new bank statement
              </Typography>
            </Paper>
          </Grid>
        </Grid>

        <Paper sx={{ mt: 4, p: 3 }}>
          <Typography variant="h6" gutterBottom>
            Quick Actions
          </Typography>
          <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <Button
              variant="outlined"
              startIcon={<UploadFile />}
              onClick={() => navigate('/upload')}
            >
              Upload Statement
            </Button>
            <Button
              variant="outlined"
              startIcon={<Description />}
              onClick={() => navigate('/statements')}
            >
              View Statements
            </Button>
            <Button
              variant="outlined"
              startIcon={<AccountBalance />}
              onClick={() => navigate('/transactions')}
            >
              View Transactions
            </Button>
          </Box>
        </Paper>
      </Container>
    </Box>
  );
};

export default Dashboard;
