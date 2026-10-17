export default function ErrorBanner({ children }) {
  if (!children) return null;
  return <div className="banner banner-error" role="alert">{children}</div>;
}
