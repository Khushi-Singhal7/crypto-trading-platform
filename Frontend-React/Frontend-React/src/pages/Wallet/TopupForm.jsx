import { paymentHandler } from "@/Redux/Wallet/Action";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Skeleton } from "@/components/ui/skeleton";
import { useState } from "react";
import { useDispatch, useSelector } from "react-redux";

const TopupForm = () => {
  const [amount, setAmount] = useState("");
  const [paymentMethod, setPaymentMethod] = useState("RAZORPAY");

  const { wallet } = useSelector((store) => store);
  const dispatch = useDispatch();

  const handleSubmit = () => {
    if (!amount) return;

    dispatch(
      paymentHandler({
        jwt: localStorage.getItem("jwt"),
        paymentMethod,
        amount,
      })
    );
  };

  return (
    <div className="pt-10 space-y-5">
      <div>
        <h1 className="pb-2">Enter Amount</h1>
        <Input
          type="number"
          placeholder="$9999"
          value={amount}
          onChange={(e) => setAmount(e.target.value)}
          className="py-7 text-lg"
        />
      </div>

      <div>
        <h1 className="pb-2">Select payment method</h1>

        <RadioGroup
          defaultValue="RAZORPAY"
          className="flex gap-4"
          onValueChange={setPaymentMethod}
        >
          {/* Razorpay */}
          <div className="flex items-center gap-3 border p-4 rounded-md w-full">
            <RadioGroupItem value="RAZORPAY" id="r1" />
            <Label htmlFor="r1" className="cursor-pointer w-full">
              <div className="bg-white rounded-md px-4 py-3 flex justify-center font-bold text-blue-600">
                Razorpay
              </div>
            </Label>
          </div>

          {/* Stripe */}
          <div className="flex items-center gap-3 border p-4 rounded-md w-full">
            <RadioGroupItem value="STRIPE" id="r2" />
            <Label htmlFor="r2" className="cursor-pointer w-full">
              <div className="bg-white rounded-md px-4 py-3 flex justify-center font-bold text-indigo-600">
                Stripe
              </div>
            </Label>
          </div>
        </RadioGroup>
      </div>

      {wallet.loading ? (
        <Skeleton className="h-14 w-full" />
      ) : (
        <Button onClick={handleSubmit} className="w-full py-7 text-xl">
          Submit
        </Button>
      )}
    </div>
  );
};

export default TopupForm;