import { useEffect, useRef } from "react";

const WarpBackground = ({ active }: { active: boolean }) => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);

  const activeRef = useRef(active);

  useEffect(() => {
    activeRef.current = active;
  }, [active]);

  useEffect(() => {
    // if (!active) return;

    const canvas = canvasRef.current!;
    const ctx = canvas.getContext("2d")!;
    let animationId: number;

    const resize = () => {
      canvas.width = window.innerWidth;
      canvas.height = window.innerHeight;
    };

    resize();
    window.addEventListener("resize", resize);

    const stars: {
      x: number;
      y: number;
      z: number;
    }[] = [];

    const STAR_COUNT = Math.floor((canvas.width * canvas.height) / 4000);

    for (let i = 0; i < STAR_COUNT; i++) {
      stars.push({
        x: (Math.random() - 0.5) * canvas.width,
        y: (Math.random() - 0.5) * canvas.height,
        z: Math.random() * canvas.width,
      });
    }

    let currentSpeed = 0;
    const maxSpeed = 10;
    let visualWarp = 0;
    let shimmerPhase = 0;

    const animate = () => {
      const targetSpeed = activeRef.current ? maxSpeed : 0;
      currentSpeed += (targetSpeed - currentSpeed) * 0.08;

      const rawWarp = currentSpeed / maxSpeed;
      // visual warp lags behind speed
      visualWarp += (rawWarp - visualWarp) * 0.04;

      const warpFactor = visualWarp;

      if (!activeRef.current && currentSpeed < 0.01) {
        currentSpeed = 0;
      }

      ctx.clearRect(0, 0, canvas.width, canvas.height);

      const isWarping = currentSpeed > 0;

      if (isWarping) {
        // motion blur only while moving
        ctx.fillStyle = "rgba(0,0,0,0.25)";
        // ctx.fillRect(0, 0, canvas.width, canvas.height);
      }

      for (let star of stars) {
        if (currentSpeed > 0) {
          star.z -= currentSpeed;
        }

        if (star.z <= 0) {
          star.x = (Math.random() - 0.5) * canvas.width;
          star.y = (Math.random() - 0.5) * canvas.height;
          star.z = canvas.width;
        } else {
          // small subtle movement when not warping
          star.x += (Math.random() - 0.5) * 0.5; // ±0.15 px
          star.y += (Math.random() - 0.5) * 0.5; // ±0.15 px
        }

        const k = 128.0 / star.z;
        const x = star.x * k + canvas.width / 2;
        const y = star.y * k + canvas.height / 2;

        const stretch = visualWarp * maxSpeed * 2;

        const px = star.x * (128.0 / (star.z + stretch)) + canvas.width / 2;
        const py = star.y * (128.0 / (star.z + stretch)) + canvas.height / 2;

        // Draw Warp Line
        if (warpFactor > 0.001) {
          ctx.strokeStyle = `rgba(255,140,0,${0.8 * warpFactor})`;
          ctx.lineWidth = 2;
          ctx.beginPath();
          ctx.moveTo(px, py);
          ctx.lineTo(x, y);
          ctx.stroke();
        }

        // Draw shimmering star dot
        shimmerPhase += 0.00005; // smaller = slower shimmer
        if (shimmerPhase > Math.PI * 2) shimmerPhase -= Math.PI * 2;

        const offset = (star.x + star.y) * 0.5;
        const minShimmer = 0.3;
        const maxShimmer = 1.0;
        const shimmer =
          minShimmer +
          (maxShimmer - minShimmer) *
            (0.5 + 0.5 * Math.sin(shimmerPhase + offset));

        const radius = 1 + Math.random() * 1.0;

        const gradient = ctx.createRadialGradient(x, y, 0, x, y, radius * 2);
        gradient.addColorStop(0, `rgba(255, 200, 50, ${shimmer})`);
        gradient.addColorStop(0.5, `rgba(255,140,0,${shimmer * 0.6})`);
        gradient.addColorStop(1, "rgba(0,0,0,0)");

        ctx.fillStyle = gradient;
        ctx.beginPath();
        ctx.arc(x, y, radius * 1, 0, Math.PI * 2);
        ctx.fill();
      }

      animationId = requestAnimationFrame(animate);
    };

    animate();

    return () => {
      cancelAnimationFrame(animationId);
      window.removeEventListener("resize", resize);
    };
  }, []);

  return (
    <canvas
      ref={canvasRef}
      className="absolute inset-0 z-0 pointer-events-none"
    />
  );
};
export default WarpBackground;
