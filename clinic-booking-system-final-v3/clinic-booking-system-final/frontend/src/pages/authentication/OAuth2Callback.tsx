import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { base } from '../../api/http';

export default function OAuth2Callback() {
  const { login } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    (async () => {
      try {
        const backendBase = base;
        
        const res = await fetch(`${backendBase}/auth/oauth2/user-info`, {
          method: 'GET',
          credentials: 'include', // Quan trọng: gửi session cookie từ OAuth2
          headers: { 
            'Accept': 'application/json',
            'Content-Type': 'application/json'
          },
        });
        
        if (!res.ok) {
          const errorText = await res.text();
          console.error('OAuth2 user-info failed:', res.status, errorText);
          throw new Error(`OAuth2 user-info failed: ${res.status}`);
        }
        
        const data = await res.json();
        
        if (!data?.token || !data?.user) {
          console.error('Invalid OAuth2 response:', data);
          throw new Error('Invalid OAuth2 response: missing token or user');
        }
        
        login(
          {
            id: String(data.user.id),
            fullName: data.user.fullName,
            email: data.user.email,
            userType: data.user.userType,
          },
          data.token
        );
        navigate('/', { replace: true });
      } catch (error) {
        console.error('OAuth2 callback error:', error);
        navigate('/login', { replace: true });
      }
    })();
  }, [login, navigate]);

  return null;
}


