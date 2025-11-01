import { stringifyError } from '@/utils/errors';
import {
  Alert,
  Avatar,
  Button,
  Card,
  Flex,
  Form,
  Input,
  Layout,
  Space,
  Typography,
} from 'antd';
import FormItem from 'antd/es/form/FormItem';
import axios from 'axios';
import { useMemo, useState } from 'react';
import { Navigate, useNavigate, useSearchParams } from 'react-router-dom';

export const ResetPasswordPage: React.FC<any> = () => {
  const navigate = useNavigate();
  const [search] = useSearchParams();

  const token = useMemo(() => search.get('token'), [search]);
  const email = useMemo(() => search.get('email'), [search]);

  const [form] = Form.useForm();
  const [error, setError] = useState<string>();
  const [loading, setLoading] = useState<boolean>(false);

  const sendResetLink = async (data: any) => {
    if (!token) return;
    setLoading(true);
    setError(undefined);
    try {
      await axios.post(`/api/auth/reset-password-with-token`, data, {
        headers: {
          Authorization: `Bearer ${token}`,
        },
      });
      navigate('/login');
    } catch (err) {
      setError(stringifyError(err, 'Oops! Something went wrong.'));
    } finally {
      setLoading(false);
    }
  };

  if (!token) {
    return <Navigate to="/forgot-password" />;
  }

  return (
    <Layout
      style={{
        padding: '10px',
        overflow: 'auto',
        height: 'calc(100vh - 40px)',
      }}
    >
      <Layout.Content style={{ overflow: 'auto' }}>
        <Flex
          align="center"
          justify="center"
          style={{ width: '100%', height: '100%' }}
        >
          <Card
            title={
              <Space
                direction="vertical"
                align="center"
                style={{ padding: '15px', width: '100%' }}
              >
                <Avatar
                  src="/lncrawl.svg"
                  style={{ width: '96px', height: '96px' }}
                />
                <Typography.Title
                  type="success"
                  level={3}
                  style={{ margin: 0 }}
                >
                  Lightnovel Crawler
                </Typography.Title>
              </Space>
            }
            style={{ width: '400px' }}
          >
            <Form
              form={form}
              onFinish={sendResetLink}
              size="large"
              layout="vertical"
              labelCol={{ style: { padding: 0 } }}
            >
              <Form.Item name="email" label="Email" initialValue={email}>
                <Input
                  disabled
                  autoComplete="email"
                  placeholder="Enter email"
                />
              </Form.Item>

              <Form.Item
                name={'password'}
                label="Password"
                rules={[
                  { required: true, message: 'Please enter a password' },
                  { min: 6, message: 'Password must be at least 6 characters' },
                ]}
                hasFeedback
              >
                <Input.Password
                  placeholder="New password"
                  autoComplete="new-password"
                />
              </Form.Item>

              {Boolean(error) && (
                <Alert
                  type="warning"
                  showIcon
                  message={error}
                  closable
                  onClose={() => setError('')}
                />
              )}

              <FormItem style={{ marginTop: '20px' }}>
                <Button
                  block
                  type="primary"
                  htmlType="submit"
                  loading={loading}
                  children={'Reset Password'}
                />
              </FormItem>
            </Form>
          </Card>
        </Flex>
      </Layout.Content>
    </Layout>
  );
};
