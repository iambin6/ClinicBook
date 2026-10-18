import { useState, useEffect } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import Input from '../../components/common/Input';
import Button from '../../components/common/Button';
import { loginApi } from '../../api/auth';
import { getErrorMessage } from '../../utils/error';
import '../../styles/login.css';
import { useToast } from '../../components/common/ToastProvider';
import { useAuth } from '../../hooks/useAuth';

export default function Login() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [failedOnce, setFailedOnce] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const { showSuccess } = useToast();
  const { login } = useAuth();

  useEffect(() => {
    const err = searchParams.get('error');
    if (err) {
      setError(decodeURIComponent(err));
    }
  }, [searchParams]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError('');
    setLoading(true);
    try {
      const res = await loginApi({ email, password });
      login(res.user, res.token);

      const role = (res?.user?.userType || res?.user?.userType?.[0] || "").toString();

      if (role === "Admin") {
        showSuccess('Đăng nhập thành công!');
        navigate("/admin/dashboard", { replace: true });
      } else if (role === "Patient") {
        showSuccess('Đăng nhập thành công!');
        // Kiểm tra xem có phải mật khẩu tạm không (có thể check qua một flag hoặc để user tự quyết định)
        // Tạm thời chuyển đến profile, user có thể vào reset password từ đó
        navigate("/patient/profile", { replace: true });
      } else if (role === "Doctor") {
        showSuccess('Đăng nhập thành công!');
        navigate("/doctor/dashboard", { replace: true });
      } else if (role === "Receptionist") {
        showSuccess('Đăng nhập thành công!');
        navigate("/receptionist/dashboard", { replace: true });
      } else {
        showSuccess('Đăng nhập thành công!');
        navigate("/", { replace: true });
      }
    } catch (err: unknown) {
      setError(getErrorMessage(err) || 'Đăng nhập thất bại');
      setFailedOnce(true);
    } finally {
      setLoading(false);
    }
  }

  const authBase = (import.meta.env.VITE_API_URL
      ? String(import.meta.env.VITE_API_URL).replace(/\/(?:api)(?:\/v1)?\/?$/, '')
      : 'http://localhost:8080');

  return (
    <section className="login-page">
      <div className="login-card">
        <h1 className="login-title">Đăng nhập</h1>
        <form onSubmit={onSubmit} className="login-form">
          <Input
            id="email"
            name="email"
            label="Email"
            type="email"
            autoComplete="username"
            value={email}
            placeholder="you@example.com"
            onChange={(e) => setEmail(e.target.value)}
            required
          />

          <Input
            id="password"
            name="password"
            label="Mật khẩu"
            type="password"
            autoComplete="current-password"
            value={password}
            placeholder="••••••••"
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          {error && (
            <div className="error-block">
              <p>{error}</p>
              {failedOnce && (
                <Link to="/forgot-password" className="btn-forgot">Quên mật khẩu?</Link>
              )}
            </div>
          )}

          <Button type="submit" disabled={loading} className="btn-auth">
            {loading ? 'Đang xử lý…' : 'Đăng nhập'}
          </Button>

          <p className="login-register">
            Chưa có tài khoản? <Link to="/register">Đăng ký ngay</Link>
          </p>

          <div className="social-group">
            <button type="button" className="btn-social btn-google" onClick={() => (window.location.href = `${authBase}/oauth2/authorization/google`)}>
              <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true">
                <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
                <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
                <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z" fill="#FBBC05"/>
                <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
              </svg>
              <span>Đăng nhập với Google</span>
            </button>
            <button type="button" className="btn-social btn-facebook" onClick={() => (window.location.href = `${authBase}/oauth2/authorization/facebook`)}>
              <svg width="18" height="18" viewBox="0 0 24 24" aria-hidden="true" fill="currentColor">
                <path d="M24 12.073c0-6.627-5.373-12-12-12s-12 5.373-12 12c0 5.99 4.388 10.954 10.125 11.854v-8.385H7.078v-3.47h3.047V9.43c0-3.007 1.792-4.669 4.533-4.669 1.312 0 2.686.235 2.686.235v2.953H15.83c-1.491 0-1.956.925-1.956 1.874v2.25h3.328l-.532 3.47h-2.796v8.385C19.612 23.027 24 18.062 24 12.073z"/>
              </svg>
              <span>Đăng nhập với Facebook</span>
            </button>
          </div>
        </form>
      </div>
    </section>
  );
}
